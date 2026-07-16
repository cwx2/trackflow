package com.trackflow.auth.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.security.ApiKeyAuthenticationToken;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.entity.ApiKey;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.ApiKeyMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * API Key 认证过滤器
 * 识别 tf_ 前缀的 Bearer Token，走 API Key 校验逻辑。
 * <p>
 * 认证成功/失败均记录审计日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "tf_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyMapper apiKeyMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final SystemAuditService systemAuditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            if (token.startsWith(API_KEY_PREFIX)) {
                // API Key 认证
                if (authenticateWithApiKey(token, request)) {
                    filterChain.doFilter(request, response);
                    return;
                } else {
                    // 记录 API Key 认证失败
                    logApiKeyFailed(request, token);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":40100,\"message\":\"Invalid API key\",\"data\":null}"
                    );
                    return;
                }
            }
        }

        // 不是 API Key，继续后续 filter（JWT 校验）
        filterChain.doFilter(request, response);
    }

    private boolean authenticateWithApiKey(String token, HttpServletRequest request) {
        // 从 token 中提取 prefix 用于快速查找
        // token 格式: tf_{prefix}{secret}
        String tokenWithoutTf = token.substring(API_KEY_PREFIX.length());
        if (tokenWithoutTf.length() < 8) return false;

        String prefix = API_KEY_PREFIX + tokenWithoutTf.substring(0, 8);

        // 根据 prefix 查找
        ApiKey apiKey = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getPrefix, prefix)
        );

        if (apiKey == null) return false;

        // 验证 hash
        if (!BCrypt.checkpw(token, apiKey.getKeyHash())) return false;

        // 验证过期
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        // 验证用户状态
        SysUser user = sysUserMapper.selectById(apiKey.getUserId());
        if (user == null || !"active".equals(user.getStatus())) return false;

        // 解析 API Key 的 scope（permissions 字段）
        Set<String> scope = parseScope(apiKey.getPermissions());
        if (scope == null) {
            // permissions 字段损坏，无法确定权限范围——拒绝认证（fail-closed）
            log.warn("API Key auth rejected due to corrupted permissions: key={}", prefix);
            return false;
        }

        // 使用自定义 ApiKeyAuthenticationToken，携带 scope 信息
        ApiKeyAuthenticationToken authToken = new ApiKeyAuthenticationToken(
                user.getUsername(),
                user.getId(),
                scope,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY"))
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 更新 last_used_at
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(apiKey);

        // 记录 API Key 认证成功审计日志
        logApiKeyUsed(user, apiKey, request);

        log.debug("API Key authenticated: user={}, key={}, scope={}", user.getUsername(), prefix, scope);
        return true;
    }

    /**
     * 记录 API Key 认证成功事件
     */
    private void logApiKeyUsed(SysUser user, ApiKey apiKey, HttpServletRequest request) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("method", "api_key");
            details.put("key_prefix", apiKey.getPrefix());
            details.put("key_name", apiKey.getName());

            systemAuditService.logAuthEvent(
                    "api_key_used",
                    user.getId(),
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log api_key_used event: {}", e.getMessage());
        }
    }

    /**
     * 记录 API Key 认证失败事件
     */
    private void logApiKeyFailed(HttpServletRequest request, String token) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("method", "api_key");
            // 只记录前缀，不泄露完整 token
            if (token.length() > 11) {
                details.put("key_prefix", token.substring(0, 11) + "...");
            }

            systemAuditService.logAuthEvent(
                    "api_key_failed",
                    null,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log api_key_failed event: {}", e.getMessage());
        }
    }

    /**
     * 解析 API Key 的 permissions JSON 字段为 scope 集合。
     * 空字符串、null、空数组 "[]" 均返回空集合（表示不限制）。
     * 解析失败时返回 null，调用方应拒绝认证（fail-closed 原则）。
     */
    private Set<String> parseScope(String permissionsJson) {
        if (permissionsJson == null || permissionsJson.isBlank()
                || "[]".equals(permissionsJson.trim())) {
            return Collections.emptySet();
        }
        try {
            List<String> permissions = objectMapper.readValue(permissionsJson, new TypeReference<>() {});
            return new HashSet<>(permissions);
        } catch (Exception e) {
            log.warn("Failed to parse API Key permissions (auth rejected): {}", permissionsJson, e);
            return null;  // 解析失败：认证拒绝
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        // 只有带 Authorization 头且 token 以 tf_ 开头时才走此过滤器
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) return true;
        String token = authHeader.substring(BEARER_PREFIX.length());
        return !token.startsWith(API_KEY_PREFIX);
    }
}

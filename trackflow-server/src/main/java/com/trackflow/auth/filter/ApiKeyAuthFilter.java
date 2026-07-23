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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * last_used_at 更新节流间隔（毫秒）。
     * 同一 API Key 在此间隔内多次请求只触发一次 updateById。
     * 参考 YouTrack 的行为：Token "Last used" 显示精度为日级别，无需每请求精确更新。
     */
    private static final long LAST_USED_THROTTLE_INTERVAL_MS = 5 * 60 * 1000L; // 5 分钟

    /**
     * 缓存最大容量，防止内存泄漏。
     * 当达到上限时，清除全部缓存（简单策略，因为 API Key 数量通常有限）。
     */
    private static final int LAST_USED_CACHE_MAX_SIZE = 1000;

    /**
     * 缓存：API Key prefix → 上次执行 updateById 的时间戳（epoch millis）。
     * 用于节流 last_used_at 的更新频率，避免高频 API 调用时产生无效写放大。
     */
    private final ConcurrentHashMap<String, Long> lastUsedUpdateCache = new ConcurrentHashMap<>();

    private final ApiKeyMapper apiKeyMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final SystemAuditService systemAuditService;
    private final com.trackflow.auth.service.RateLimitService rateLimitService;

    /**
     * API Key 认证失败的原因枚举（内部使用，用于区分审计事件类型）
     */
    private enum ApiKeyFailureReason {
        /** Key 过期 */
        EXPIRED,
        /** Key 不存在、hash 不匹配、用户被禁用等通用失败 */
        INVALID
    }

    /** ThreadLocal 用于传递认证失败的具体原因 */
    private static final ThreadLocal<ApiKeyFailureReason> failureReason = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            if (token.startsWith(API_KEY_PREFIX)) {
                // API Key 认证
                failureReason.remove();
                if (authenticateWithApiKey(token, request)) {
                    filterChain.doFilter(request, response);
                    return;
                } else {
                    // 根据失败原因记录不同的审计事件
                    ApiKeyFailureReason reason = failureReason.get();
                    failureReason.remove();

                    // 记录认证失败到限流服务
                    String clientIp = WebUtils.getClientIp(request);
                    rateLimitService.recordAuthFailure(clientIp);

                    if (reason == ApiKeyFailureReason.EXPIRED) {
                        logApiKeyExpired(request, token);
                    } else {
                        logApiKeyFailed(request, token);
                    }

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
            failureReason.set(ApiKeyFailureReason.EXPIRED);
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

        // 认证成功，清除该 IP 的失败计数
        rateLimitService.clearAuthFailures(WebUtils.getClientIp(request));

        // 节流更新 last_used_at：同一 API Key 在 5 分钟内只执行一次 updateById
        updateLastUsedAtThrottled(apiKey, prefix);

        // 记录 API Key 认证成功审计日志
        logApiKeyUsed(user, apiKey, request);

        log.debug("API Key authenticated: user={}, key={}, scope={}", user.getUsername(), prefix, scope);
        return true;
    }

    /**
     * 节流更新 last_used_at。
     * <p>
     * 同一 API Key 在 LAST_USED_THROTTLE_INTERVAL_MS 间隔内多次请求，
     * 只触发一次 updateById，以避免高频 API 调用场景下的无效数据库写放大。
     * <p>
     * 首次使用的 API Key 会立即更新 last_used_at。
     *
     * @param apiKey API Key 实体
     * @param prefix API Key 前缀（用作缓存 key）
     */
    private void updateLastUsedAtThrottled(ApiKey apiKey, String prefix) {
        long now = System.currentTimeMillis();
        Long lastUpdate = lastUsedUpdateCache.get(prefix);

        // 首次使用或距上次更新超过阈值时，执行 updateById
        if (lastUpdate == null || (now - lastUpdate) >= LAST_USED_THROTTLE_INTERVAL_MS) {
            apiKey.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(apiKey);

            // 缓存满时清除全部（API Key 数量通常有限，简单策略即可）
            if (lastUsedUpdateCache.size() >= LAST_USED_CACHE_MAX_SIZE) {
                lastUsedUpdateCache.clear();
                log.debug("lastUsedUpdateCache cleared due to reaching max size: {}", LAST_USED_CACHE_MAX_SIZE);
            }
            lastUsedUpdateCache.put(prefix, now);

            log.debug("Updated last_used_at for API Key: {}", prefix);
        } else {
            log.debug("Skipped last_used_at update for API Key: {} (throttled, {}ms since last update)",
                    prefix, now - lastUpdate);
        }
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
     * 记录 API Key 认证失败事件（通用失败：key 不存在、hash 不匹配、用户被禁用）
     */
    private void logApiKeyFailed(HttpServletRequest request, String token) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("method", "api_key");
            details.put("reason", "invalid");
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
     * 记录 API Key 过期事件（区分于通用认证失败）
     */
    private void logApiKeyExpired(HttpServletRequest request, String token) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("method", "api_key");
            details.put("reason", "expired");
            if (token.length() > 11) {
                details.put("key_prefix", token.substring(0, 11) + "...");
            }

            systemAuditService.logAuthEvent(
                    "api_key_expired",
                    null,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    details
            );
        } catch (Exception e) {
            log.warn("Failed to log api_key_expired event: {}", e.getMessage());
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

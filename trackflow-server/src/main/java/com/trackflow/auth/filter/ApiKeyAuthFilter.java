package com.trackflow.auth.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.system.entity.ApiKey;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.ApiKeyMapper;
import com.trackflow.system.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * API Key 认证过滤器
 * 识别 tf_ 前缀的 Bearer Token，走 API Key 校验逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_PREFIX = "tf_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyMapper apiKeyMapper;
    private final SysUserMapper sysUserMapper;

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

        // 设置 SecurityContext
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY"))
        );
        authToken.setDetails(user.getId());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 更新 last_used_at
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyMapper.updateById(apiKey);

        log.debug("API Key authenticated: user={}, key={}", user.getUsername(), prefix);
        return true;
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

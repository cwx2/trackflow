package com.trackflow.auth.filter;

import com.trackflow.auth.service.UserSyncService;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户同步过滤器：在 JWT 认证成功后同步用户信息到本地数据库，
 * 并将本地用户 ID 设置到 Authentication details 中。
 * <p>
 * 同时负责记录认证安全审计日志（login / first_login）。
 * 使用 JWT ID (jti) 去重，确保每个 token 只记录一次登录事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserSyncService userSyncService;
    private final SystemAuditService systemAuditService;

    /**
     * 已记录过登录事件的 JWT ID 缓存。
     * key: jti (JWT ID), value: 写入时间戳（用于定期清理过期条目）。
     * 由于 Keycloak token 有效期为 5 分钟，缓存条目在 10 分钟后自动清理。
     */
    private final ConcurrentHashMap<String, Long> loggedTokenIds = new ConcurrentHashMap<>();

    /** 缓存条目过期时间：10 分钟（毫秒） */
    private static final long CACHE_EXPIRY_MS = 10 * 60 * 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth && jwtAuth.isAuthenticated()) {
            try {
                Jwt jwt = jwtAuth.getToken();
                SysUser user = userSyncService.syncFromJwt(jwt);
                // 将本地用户 ID 设置到 details 中，供后续 SecurityUtils 获取
                jwtAuth.setDetails(user.getId());

                // 仅对"新颁发"的 token 记录登录事件（jti 去重 + 时间窗口）
                if (shouldLogLogin(jwt)) {
                    logLoginEvent(user, jwt, request);
                }
            } catch (Exception e) {
                log.error("User sync failed: {}", e.getMessage());
                // 如果是 BusinessException（如用户被禁用），向上抛出
                if (e instanceof com.trackflow.common.exception.BusinessException) {
                    // 记录登录失败事件（用户被禁用）
                    logLoginFailedFromJwt(request, "user_disabled");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":40301,\"message\":\"User account is disabled\",\"data\":null}"
                    );
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否应为此 JWT 记录登录事件。
     * 条件：
     * 1. token 是新颁发的（issued_at 在 120 秒内）
     * 2. 此 token 的 jti 尚未被记录过（避免同一 token 多次请求重复记录）
     */
    private boolean shouldLogLogin(Jwt jwt) {
        if (jwt.getIssuedAt() == null) return false;

        // 条件1: token 必须是新颁发的
        long issuedAtSeconds = jwt.getIssuedAt().getEpochSecond();
        long nowSeconds = java.time.Instant.now().getEpochSecond();
        if ((nowSeconds - issuedAtSeconds) > 120) return false;

        // 条件2: jti 去重
        String jti = jwt.getId();
        if (jti == null) {
            // 没有 jti 的 token 仅靠时间窗口判断（fallback）
            return true;
        }

        // 定期清理过期条目（简单概率清理，避免内存泄漏）
        if (loggedTokenIds.size() > 100) {
            long cutoff = System.currentTimeMillis() - CACHE_EXPIRY_MS;
            loggedTokenIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }

        // putIfAbsent 返回 null 表示首次插入
        return loggedTokenIds.putIfAbsent(jti, System.currentTimeMillis()) == null;
    }

    /**
     * 记录 JWT 登录成功审计事件。
     * 仅在新 token 首次使用时调用（由 isNewToken 判断）。
     */
    private void logLoginEvent(SysUser user, Jwt jwt, HttpServletRequest request) {
        try {
            // 判断是否为首次登录：createdAt 和 lastLoginAt 差值极小（同一事务内设置）
            boolean isFirstLogin = user.getCreatedAt() != null
                    && user.getLastLoginAt() != null
                    && java.time.Duration.between(user.getCreatedAt(), user.getLastLoginAt()).abs().getSeconds() <= 2;

            String action = isFirstLogin ? "first_login" : "login";

            systemAuditService.logAuthEvent(
                    action,
                    user.getId(),
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "username", user.getUsername())
            );
        } catch (Exception e) {
            // 审计日志失败不阻塞正常请求
            log.warn("Failed to log auth event for user {}: {}", user.getUsername(), e.getMessage());
        }
    }

    /**
     * 记录 JWT 登录失败事件（如用户被禁用）
     */
    private void logLoginFailedFromJwt(HttpServletRequest request, String reason) {
        try {
            systemAuditService.logAuthEvent(
                    "login_failed",
                    null,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "reason", reason)
            );
        } catch (Exception e) {
            log.warn("Failed to log auth failure event: {}", e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 跳过不需要认证的路径
        String path = request.getRequestURI();
        return path.startsWith("/doc.html")
                || path.startsWith("/webjars/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator/");
    }
}

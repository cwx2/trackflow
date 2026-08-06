package com.trackflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Back-Channel Logout 服务 — 处理 Keycloak Back-Channel Logout 回调。
 * <p>
 * 负责验证 logout_token、将 session 写入 Redis 黑名单、失效权限缓存、记录审计日志。
 * 根据 OIDC 规范，此服务内部的所有异常都被安全消化（不向调用方抛出），
 * 因为 Back-Channel Logout 端点必须始终返回 200 OK。
 *
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">OIDC Back-Channel Logout Spec</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackChannelLogoutService {

    private final JwtDecoder jwtDecoder;
    private final SysUserMapper sysUserMapper;
    private final SystemAuditService systemAuditService;
    private final StringRedisTemplate redisTemplate;
    private final PermissionService permissionService;

    /** Redis key 前缀：已注销的 session ID 黑名单 */
    public static final String LOGOUT_SESSION_KEY_PREFIX = "auth:logout:session:";

    /** 黑名单 TTL：覆盖 JWT 最大有效期（Keycloak 默认 5 分钟 + 1 分钟缓冲） */
    private static final Duration LOGOUT_SESSION_TTL = Duration.ofMinutes(6);

    /**
     * 处理 back-channel logout 请求。
     * <p>
     * 内部捕获所有异常——OIDC 规范要求端点始终返回 200，
     * 因此本方法保证不向调用方抛出任何异常。
     *
     * @param logoutToken Keycloak 签发的 logout_token JWT 字符串
     * @param request     HTTP 请求（用于审计日志记录 IP/UA）
     */
    public void processLogout(String logoutToken, HttpServletRequest request) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(logoutToken);
        } catch (JwtException e) {
            log.warn("Back-channel logout token standard validation failed: {}. " +
                    "This may be due to logout_token having different claims than access_token.", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Back-channel logout token decode error", e);
            return;
        }
        processVerifiedToken(jwt, request);
    }

    /**
     * 处理已验证的 logout token：
     * 1. 提取 sub (keycloak_id) 和 sid (session_id)
     * 2. 将 session ID 写入 Redis 黑名单（核心：使 JWT 即时失效）
     * 3. 失效用户权限缓存
     * 4. 记录审计日志
     */
    private void processVerifiedToken(Jwt jwt, HttpServletRequest request) {
        String keycloakUserId = jwt.getSubject();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            log.warn("Back-channel logout token missing 'sub' claim");
            return;
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getKeycloakId, keycloakUserId)
        );

        Long userId = user != null ? user.getId() : null;
        String username = user != null ? user.getUsername() : "unknown";

        String sessionId = extractSessionId(jwt);

        invalidateSession(sessionId, keycloakUserId);

        if (userId != null) {
            try {
                permissionService.invalidateCache(userId);
            } catch (Exception e) {
                log.warn("Failed to invalidate permission cache for userId={}: {}", userId, e.getMessage());
            }
        }

        systemAuditService.logAuthEvent(
                "logout",
                userId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                Map.of(
                        "method", "backchannel_logout",
                        "keycloak_id", keycloakUserId,
                        "username", username,
                        "session_id", sessionId
                )
        );

        log.info("Back-channel logout processed: keycloakId={}, username={}, sessionId={}",
                keycloakUserId, username, sessionId);
    }

    private String extractSessionId(Jwt jwt) {
        Object sidClaim = jwt.getClaim("sid");
        return sidClaim != null ? sidClaim.toString() : "";
    }

    /**
     * 将 session 写入 Redis 黑名单。
     * <p>
     * 优先使用 session ID (sid) 作为 key——精确到单个 session，不影响同用户的其他活跃 session。
     * 如果 sid 为空（兜底），使用 keycloak_id 作为 key——会使该用户所有 session 失效。
     * <p>
     * TTL 设为 6 分钟（覆盖 Keycloak JWT 默认 5 分钟有效期 + 时钟偏差缓冲）。
     * Redis 不可用时降级为仅日志记录，不阻塞 logout 响应。
     */
    private void invalidateSession(String sessionId, String keycloakUserId) {
        try {
            if (sessionId != null && !sessionId.isBlank()) {
                redisTemplate.opsForValue().set(
                        LOGOUT_SESSION_KEY_PREFIX + sessionId,
                        keycloakUserId,
                        LOGOUT_SESSION_TTL
                );
                log.debug("Session blacklisted: sid={}, keycloakId={}", sessionId, keycloakUserId);
            } else {
                String fallbackKey = "auth:logout:user:" + keycloakUserId;
                redisTemplate.opsForValue().set(
                        fallbackKey,
                        String.valueOf(System.currentTimeMillis()),
                        LOGOUT_SESSION_TTL
                );
                log.warn("Session ID not available in logout token, using user-level blacklist: keycloakId={}",
                        keycloakUserId);
            }
        } catch (Exception e) {
            log.error("Failed to write session blacklist to Redis: {}", e.getMessage());
        }
    }
}

package com.trackflow.auth.controller;

import com.trackflow.auth.security.NoAuthorizationRequired;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.model.R;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Keycloak Back-Channel Logout 端点。
 * <p>
 * 当用户从 Keycloak 登出时，Keycloak 向注册了 Back-Channel Logout URL 的 client
 * 发送 POST 请求，携带 logout_token（JWT 格式，与 access_token 使用相同 JWKS 签名）。
 * <p>
 * 本端点：
 * 1. 验证 logout_token 签名（使用已配置的 JwtDecoder，共享 Keycloak JWKS）
 * 2. 从 token 中提取 sub claim（Keycloak user ID）
 * 3. 记录 logout 审计事件
 * <p>
 * 安全说明：此端点无需 Bearer Token 认证（Keycloak 是 server-to-server 调用），
 * 但通过验证 logout_token 的 JWT 签名确保请求来源合法。
 * 如果 JWT 校验失败（如 logout_token 的 typ/aud 不满足 resource server 验证），
 * 仍返回 200（OIDC 规范要求），仅记录警告日志。
 *
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">OIDC Back-Channel Logout Spec</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class BackChannelLogoutController {

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
     * Keycloak Back-Channel Logout 回调端点。
     * <p>
     * Keycloak 发送 application/x-www-form-urlencoded 格式，
     * 参数名为 "logout_token"，值为 JWT 字符串。
     *
     * @param logoutToken Keycloak 签发的 logout_token（JWT）
     * @return 200 OK 表示成功处理（OIDC 规范要求返回 200）
     */
    @NoAuthorizationRequired(reason = "Keycloak server-to-server 回调，通过验证 logout_token JWT 签名确保合法性")
    @PostMapping("/backchannel-logout")
    public R<Void> handleBackChannelLogout(
            @RequestParam("logout_token") String logoutToken,
            HttpServletRequest request) {

        try {
            // 验证 logout_token 的签名（使用与 access_token 相同的 Keycloak JWKS）
            Jwt jwt = jwtDecoder.decode(logoutToken);
            processLogoutToken(jwt, request);
        } catch (JwtException e) {
            // logout_token 可能有不同的 typ/aud 导致标准 resource server 验证失败。
            // 尝试从未验证的 payload 中提取 sub（仅在签名验证失败时降级处理）。
            log.warn("Back-channel logout token standard validation failed: {}. " +
                    "This may be due to logout_token having different claims than access_token.", e.getMessage());
            // 安全降级：仅记录日志，不从未验证 token 提取数据
            // 如果需要完整支持，应配置 Keycloak Logout Token 专用 Decoder
        } catch (Exception e) {
            log.error("Back-channel logout processing error", e);
        }

        // OIDC 规范要求无论成功失败都返回 200
        return R.ok();
    }

    /**
     * 处理已验证的 logout token：
     * 1. 提取 sub (keycloak_id) 和 sid (session_id)
     * 2. 将 session ID 写入 Redis 黑名单（核心：使 JWT 即时失效）
     * 3. 失效用户权限缓存
     * 4. 记录审计日志
     */
    private void processLogoutToken(Jwt jwt, HttpServletRequest request) {
        String keycloakUserId = jwt.getSubject();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            log.warn("Back-channel logout token missing 'sub' claim");
            return;
        }

        // 根据 keycloak_id 查找本地用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getKeycloakId, keycloakUserId)
        );

        Long userId = user != null ? user.getId() : null;
        String username = user != null ? user.getUsername() : "unknown";

        // 提取 session_id（sid claim）
        String sessionId = extractSessionId(jwt);

        // ★ 核心：将 session ID 写入 Redis 黑名单，使该 session 的 JWT 即时失效
        invalidateSession(sessionId, keycloakUserId);

        // 失效该用户的权限缓存（即使 JWT 被拦截，也确保权限数据不再有效）
        if (userId != null) {
            try {
                permissionService.invalidateCache(userId);
            } catch (Exception e) {
                log.warn("Failed to invalidate permission cache for userId={}: {}", userId, e.getMessage());
            }
        }

        // 记录 logout 审计事件
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

    /**
     * 从 JWT 中提取 session ID (sid claim)
     */
    private String extractSessionId(Jwt jwt) {
        try {
            Object sidClaim = jwt.getClaim("sid");
            if (sidClaim != null) {
                return sidClaim.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 将 session 写入 Redis 黑名单。
     * <p>
     * 优先使用 session ID (sid) 作为 key——精确到单个 session，不影响同用户的其他活跃 session。
     * 如果 sid 为空（兜底），使用 keycloak_id 作为 key——会使该用户所有 session 失效。
     * <p>
     * TTL 设为 6 分钟（覆盖 Keycloak JWT 默认 5 分钟有效期 + 时钟偏差缓冲）。
     * 过期后自动清理，不会积累。
     */
    private void invalidateSession(String sessionId, String keycloakUserId) {
        try {
            if (sessionId != null && !sessionId.isBlank()) {
                // 精确：按 session ID 失效
                redisTemplate.opsForValue().set(
                        LOGOUT_SESSION_KEY_PREFIX + sessionId,
                        keycloakUserId,
                        LOGOUT_SESSION_TTL
                );
                log.debug("Session blacklisted: sid={}, keycloakId={}", sessionId, keycloakUserId);
            } else {
                // 兜底：按 keycloak_id 失效（影响该用户所有 session）
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
            // Redis 不可用时降级为当前行为（仅记录日志，不阻塞 logout 响应）
            log.error("Failed to write session blacklist to Redis: {}", e.getMessage());
        }
    }
}

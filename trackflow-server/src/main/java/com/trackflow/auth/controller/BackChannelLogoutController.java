package com.trackflow.auth.controller;

import com.trackflow.common.model.R;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Keycloak Back-Channel Logout 回调端点。
     * <p>
     * Keycloak 发送 application/x-www-form-urlencoded 格式，
     * 参数名为 "logout_token"，值为 JWT 字符串。
     *
     * @param logoutToken Keycloak 签发的 logout_token（JWT）
     * @return 200 OK 表示成功处理（OIDC 规范要求返回 200）
     */
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
     * 处理已验证的 logout token
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

        // 提取 session_id（sid claim，如有）
        String sessionId = "";
        try {
            Object sidClaim = jwt.getClaim("sid");
            if (sidClaim != null) {
                sessionId = sidClaim.toString();
            }
        } catch (Exception ignored) {}

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

        log.info("Back-channel logout processed: keycloakId={}, username={}", keycloakUserId, username);
    }
}

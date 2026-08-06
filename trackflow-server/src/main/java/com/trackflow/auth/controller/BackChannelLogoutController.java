package com.trackflow.auth.controller;

import com.trackflow.auth.security.NoAuthorizationRequired;
import com.trackflow.auth.service.BackChannelLogoutService;
import com.trackflow.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Keycloak Back-Channel Logout 端点。
 * <p>
 * 当用户从 Keycloak 登出时，Keycloak 向注册了 Back-Channel Logout URL 的 client
 * 发送 POST 请求，携带 logout_token（JWT 格式，与 access_token 使用相同 JWKS 签名）。
 * <p>
 * 本端点：
 * 1. 验证 logout_token 签名（使用已配置的 JwtDecoder，共享 Keycloak JWKS）
 * 2. 从 token 中提取 sub claim（Keycloak user ID）
 * 3. 将 session 写入 Redis 黑名单（使 JWT 即时失效）
 * 4. 记录 logout 审计事件
 * <p>
 * 安全说明：此端点无需 Bearer Token 认证（Keycloak 是 server-to-server 调用），
 * 但通过验证 logout_token 的 JWT 签名确保请求来源合法。
 * <p>
 * OIDC 规范要求：无论成功失败都返回 200 OK。
 *
 * @see <a href="https://openid.net/specs/openid-connect-backchannel-1_0.html">OIDC Back-Channel Logout Spec</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class BackChannelLogoutController {

    private final BackChannelLogoutService backChannelLogoutService;

    /**
     * Keycloak Back-Channel Logout 回调端点。
     * <p>
     * Keycloak 发送 application/x-www-form-urlencoded 格式，
     * 参数名为 "logout_token"，值为 JWT 字符串。
     * <p>
     * OIDC 规范要求无论处理成功或失败都必须返回 200 OK。
     * 所有异常处理在 BackChannelLogoutService 内部完成。
     *
     * @param logoutToken Keycloak 签发的 logout_token（JWT）
     * @return 200 OK（OIDC 规范要求）
     */
    @NoAuthorizationRequired(reason = "Keycloak server-to-server 回调，通过验证 logout_token JWT 签名确保合法性")
    @PostMapping("/backchannel-logout")
    public R<Void> handleBackChannelLogout(
            @RequestParam("logout_token") String logoutToken,
            HttpServletRequest request) {
        backChannelLogoutService.processLogout(logoutToken, request);
        return R.ok();
    }
}

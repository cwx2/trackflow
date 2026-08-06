package com.trackflow.integration.controller;

import com.trackflow.auth.security.NoAuthorizationRequired;
import com.trackflow.integration.service.EmailMuteTokenService;
import com.trackflow.integration.service.NotificationUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 邮件静音 token 处理 Controller。
 * <p>
 * 提供无需登录的邮件静音端点：用户点击邮件底部的"静音此工单"链接后，
 * 后端验证 token，执行静音，然后重定向到前端成功页面。
 * <p>
 * 注意：此端点在 SecurityConfig 中配置为 permitAll，
 * 通过 token 机制保证安全性（token 绑定 userId + resourceId + 过期时间）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class EmailMuteController {

    private final EmailMuteTokenService emailMuteTokenService;
    private final NotificationUrlBuilder notificationUrlBuilder;

    @Value("${trackflow.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 通过邮件 token 静音工单通知，然后重定向到前端。
     * <p>
     * 此端点无需认证（token 本身携带身份信息）。
     * 在 SecurityConfig 中配置为 permitAll。
     * 访问后：
     * - token 有效 → 执行静音 → 重定向到 /settings/notifications?muted=1
     * - token 无效/过期 → 重定向到 /settings/notifications?error=expired
     * <p>
     * 注意：此端点返回 HTTP 重定向而非 JSON，无法使用 GlobalExceptionHandler 统一处理。
     * 异常处理逻辑已下沉到 EmailMuteTokenService.muteViaTokenSafe()，Controller 仅负责重定向。
     */
    @NoAuthorizationRequired
    @GetMapping("/mute-via-email")
    public void muteViaEmail(@RequestParam("token") String token, HttpServletResponse response) throws IOException {
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;

        EmailMuteTokenService.MuteOutcome outcome = emailMuteTokenService.muteViaTokenSafe(token);
        String redirectUrl;
        if (outcome.success()) {
            redirectUrl = base + "/settings/notifications?muted=1&resourceType="
                    + URLEncoder.encode(outcome.resourceType(), StandardCharsets.UTF_8)
                    + "&resourceId=" + outcome.resourceId();
        } else {
            redirectUrl = base + "/settings/notifications?error=" + outcome.errorCode();
        }
        response.sendRedirect(redirectUrl);
    }
}

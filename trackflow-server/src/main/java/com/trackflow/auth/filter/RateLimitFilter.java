package com.trackflow.auth.filter;

import com.trackflow.auth.service.RateLimitService;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 请求频率限制过滤器
 * <p>
 * 在 SecurityFilterChain 中位于 ApiKeyAuthFilter 之前执行，提供两层限流保护：
 * <ol>
 *   <li><b>认证封禁检查</b>：仅对<b>未携带有效 Token</b>的请求检查。
 *       携带 Bearer Token 或 API Key 的请求跳过此检查，让后续认证过滤器验证 Token 有效性。
 *       这确保已登录用户在 IP 被封禁期间仍可正常使用系统。</li>
 *   <li><b>全局 API 频率</b>：对所有请求（无论是否认证）检查同一 IP 每分钟请求次数</li>
 * </ol>
 * <p>
 * 设计理念参考 YouTrack 的 "Throttling by Login" 策略：
 * 认证封禁只限制登录尝试，不影响已认证用户的业务操作。
 * <p>
 * 可通过 {@code trackflow.rate-limit.enabled=false} 在开发环境完全禁用限流（默认 true）。
 * <p>
 * 认证失败计数由 {@link ApiKeyAuthFilter} 和 {@link SecurityConfig} 中的 AuthenticationEntryPoint
 * 在认证失败时主动调用 {@link RateLimitService#recordAuthFailure(String)} 实现。
 * <p>
 * 参考 OpenProject Rack::Attack 实现设计。
 *
 * @see RateLimitService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final SystemAuditService systemAuditService;

    @Value("${trackflow.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 限流开关关闭时（开发环境）直接放行，不做任何限流检查
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = WebUtils.getClientIp(request);

        // 第一层：认证封禁检查
        // 核心改进：只对未携带 Token 的请求进行 Auth Ban 检查
        // 携带 Bearer Token 或 API Key 的请求跳过此检查，让后续过滤器验证 Token 有效性
        // 这样已登录用户在 IP 被封禁期间仍可正常使用系统（符合 YouTrack 的 "Throttling by Login" 策略）
        if (!hasAuthorizationToken(request) && rateLimitService.isAuthBanned(clientIp)) {
            long retryAfter = rateLimitService.getBanRemainingSeconds(clientIp);
            writeRateLimitResponse(response, retryAfter,
                    "登录尝试过于频繁，请 " + retryAfter + " 秒后重试");
            log.debug("Rate limit: blocked banned IP {} (no auth token)", clientIp);
            return;
        }

        // 第二层：全局 API 频率检查（对所有请求生效）
        if (rateLimitService.isGlobalApiLimited(clientIp)) {
            long retryAfter = rateLimitService.getGlobalApiWindowSeconds();
            writeRateLimitResponse(response, retryAfter,
                    "请求过于频繁，请 " + retryAfter + " 秒后重试");

            // 记录限流审计事件（仅首次触发记录，避免日志风暴）
            logRateLimitEvent(request, clientIp, "global_api_limited");
            log.debug("Rate limit: global API limit exceeded for IP {}", clientIp);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查请求是否携带认证 Token（Bearer Token 或 API Key）。
     * <p>
     * 此方法不验证 Token 有效性（交给后续过滤器），只检查是否存在。
     * 用于决定是否跳过 Auth Ban 检查。
     *
     * @param request HTTP 请求
     * @return true 表示携带了 Token
     */
    private boolean hasAuthorizationToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    /**
     * 写入 429 Too Many Requests 响应
     */
    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds,
                                        String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(buildErrorJson(42900, message));
    }

    /**
     * 构建统一 JSON 错误响应
     */
    private static String buildErrorJson(int code, String message) {
        return "{\"code\":" + code
                + ",\"message\":\"" + message + "\""
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
    }

    /**
     * 记录限流事件到审计日志（防止日志风暴，同一 IP 只记录一次）
     */
    private void logRateLimitEvent(HttpServletRequest request, String clientIp, String reason) {
        try {
            systemAuditService.logAuthEvent(
                    "rate_limited",
                    null,
                    clientIp,
                    request.getHeader("User-Agent"),
                    Map.of("reason", reason, "path", request.getRequestURI())
            );
        } catch (Exception e) {
            // 审计日志失败不阻塞限流逻辑
            log.warn("Failed to log rate_limited event: {}", e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 限流开关关闭时（开发环境）跳过所有限流检查
        if (!rateLimitEnabled) {
            return true;
        }
        String path = request.getRequestURI();
        // 只对 /api/v1/ 路径启用限流，不拦截静态资源和 actuator
        return !path.startsWith("/api/v1/");
    }
}

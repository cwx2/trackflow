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
 *   <li><b>封禁检查</b>：对已被封禁的 IP 直接返回 429，不执行后续认证逻辑（避免 DB 查询消耗）</li>
 *   <li><b>全局 API 频率</b>：同一 IP 每分钟请求超过 200 次返回 429</li>
 * </ol>
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

        // 第一层：检查认证封禁（认证失败过多触发）
        if (rateLimitService.isAuthBanned(clientIp)) {
            long retryAfter = rateLimitService.getBanRemainingSeconds(clientIp);
            writeRateLimitResponse(response, retryAfter,
                    "登录尝试过于频繁，请 " + retryAfter + " 秒后重试");
            log.debug("Rate limit: blocked banned IP {}", clientIp);
            return;
        }

        // 第二层：全局 API 频率检查
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

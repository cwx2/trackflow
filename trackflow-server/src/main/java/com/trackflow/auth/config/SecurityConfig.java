package com.trackflow.auth.config;

import com.trackflow.auth.filter.ApiKeyAuthFilter;
import com.trackflow.auth.filter.UserSyncFilter;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/**
 * Spring Security 配置
 * - 无状态 JWT 校验
 * - Keycloak OIDC Resource Server
 * - 自定义 AuthenticationEntryPoint / AccessDeniedHandler 确保返回统一 JSON 格式
 * - 认证失败事件写入审计日志
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final UserSyncFilter userSyncFilter;
    private final SystemAuditService systemAuditService;

    public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter,
                          UserSyncFilter userSyncFilter,
                          SystemAuditService systemAuditService) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.userSyncFilter = userSyncFilter;
        this.systemAuditService = systemAuditService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})  // 启用 Spring Security CORS，委托给 WebMvcConfigurer 中的配置
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/actuator/health",
                    "/favicon.ico"
                ).permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll()
            )
            // API Key 过滤器：在 JWT 认证之前处理 tf_ 前缀的 token
            .addFilterBefore(apiKeyAuthFilter, BearerTokenAuthenticationFilter.class)
            // 用户同步过滤器：在 JWT 认证之后同步用户信息到本地数据库
            .addFilterAfter(userSyncFilter, BearerTokenAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2
                // 自定义 BearerTokenResolver：跳过 tf_ 前缀的 token，让 ApiKeyAuthFilter 处理
                .bearerTokenResolver(apiKeyAwareBearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                .authenticationEntryPoint((request, response, authException) -> {
                    // 记录认证失败审计日志（仅对 /api/v1/ 请求，排除静态资源）
                    logAuthFailure(request, authException);

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(buildErrorJson(
                            ErrorCode.AUTH_MISSING.getCode(), "未认证，请先登录"));
                })
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // 兜底入口点（非 OAuth2 路径的认证失败）
                    logAuthFailure(request, authException);

                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(buildErrorJson(
                            ErrorCode.AUTH_MISSING.getCode(), "未认证，请先登录"));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(buildErrorJson(
                            ErrorCode.ACCESS_DENIED.getCode(), "权限不足，您没有执行此操作的权限"));
                })
            );

        return http.build();
    }

    /**
     * 记录 JWT 认证失败事件到审计日志。
     * 仅记录带有 Authorization header 的 /api/v1/ 请求（排除无 token 的未认证请求和 API Key 路径）。
     */
    private void logAuthFailure(HttpServletRequest request, Exception authException) {
        try {
            String path = request.getRequestURI();
            String authHeader = request.getHeader("Authorization");

            // 仅记录携带了 token 但认证失败的请求（无 token 的请求不算"失败"，只是"未认证"）
            // 且 API Key 失败已在 ApiKeyAuthFilter 中单独记录
            if (authHeader == null || !authHeader.startsWith("Bearer ")
                    || !path.startsWith("/api/v1/")) {
                return;
            }

            String token = authHeader.substring(7);
            // API Key 的失败已在 ApiKeyAuthFilter 中记录
            if (token.startsWith("tf_")) {
                return;
            }

            String reason = authException != null ? authException.getClass().getSimpleName() : "unknown";
            systemAuditService.logAuthEvent(
                    "login_failed",
                    null,
                    WebUtils.getClientIp(request),
                    request.getHeader("User-Agent"),
                    Map.of("method", "jwt", "reason", reason, "path", path)
            );
        } catch (Exception e) {
            // 审计日志失败不应影响认证流程
        }
    }

    /**
     * JWT 认证转换器：从 Keycloak JWT 中提取角色信息
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * 构建统一 JSON 错误响应（避免引入 ObjectMapper 依赖）
     */
    private static String buildErrorJson(int code, String message) {
        return "{\"code\":" + code
                + ",\"message\":\"" + message + "\""
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
    }

    /**
     * 自定义 BearerTokenResolver：对 tf_ 前缀的 API Key token 返回 null，
     * 使 Spring Security 的 BearerTokenAuthenticationFilter 跳过处理，
     * 由 ApiKeyAuthFilter 负责认证。
     */
    @Bean
    public BearerTokenResolver apiKeyAwareBearerTokenResolver() {
        return (HttpServletRequest request) -> {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (token.startsWith("tf_")) {
                    // API Key token — 返回 null 让 JWT filter 跳过
                    return null;
                }
                return token;
            }
            return null;
        };
    }

    /**
     * 禁止 ApiKeyAuthFilter 被 Spring Boot 自动注册为 Servlet Filter。
     * 只通过 Security Filter Chain (addFilterBefore) 调用，避免双重执行。
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> disableApiKeyFilterAutoRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(apiKeyAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 禁止 UserSyncFilter 被 Spring Boot 自动注册为 Servlet Filter。
     * 只通过 Security Filter Chain (addFilterAfter) 调用，避免双重执行。
     */
    @Bean
    public FilterRegistrationBean<UserSyncFilter> disableUserSyncFilterAutoRegistration() {
        FilterRegistrationBean<UserSyncFilter> registration = new FilterRegistrationBean<>(userSyncFilter);
        registration.setEnabled(false);
        return registration;
    }
}

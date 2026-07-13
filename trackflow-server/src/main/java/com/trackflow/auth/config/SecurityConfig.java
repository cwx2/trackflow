package com.trackflow.auth.config;

import com.trackflow.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * - 无状态 JWT 校验
 * - Keycloak OIDC Resource Server
 * - 自定义 AuthenticationEntryPoint / AccessDeniedHandler 确保返回统一 JSON 格式
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
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
}

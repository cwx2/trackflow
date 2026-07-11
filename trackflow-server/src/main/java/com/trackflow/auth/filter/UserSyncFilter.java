package com.trackflow.auth.filter;

import com.trackflow.auth.service.UserSyncService;
import com.trackflow.system.entity.SysUser;
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

/**
 * 用户同步过滤器：在 JWT 认证成功后同步用户信息到本地数据库，
 * 并将本地用户 ID 设置到 Authentication details 中
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserSyncService userSyncService;

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
            } catch (Exception e) {
                log.error("User sync failed: {}", e.getMessage());
                // 如果是 BusinessException（如用户被禁用），向上抛出
                if (e instanceof com.trackflow.common.exception.BusinessException) {
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

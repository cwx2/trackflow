package com.trackflow.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 安全工具类：获取当前认证用户信息
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前认证用户 ID（从 SecurityContext 的 principal 属性中提取）
     * 用户 ID 在 UserSyncService 中设置到 Authentication details
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }
        // Fallback: 如果 details 中没有，尝试从 JWT sub claim 中获取（这种情况下返回 null）
        return null;
    }

    /**
     * 获取当前 JWT
     */
    public static Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
    }

    /**
     * 判断当前用户是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}

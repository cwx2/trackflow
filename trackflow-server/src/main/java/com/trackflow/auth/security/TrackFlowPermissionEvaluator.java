package com.trackflow.auth.security;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;

/**
 * 自定义权限评估器：集成到 Spring Security @PreAuthorize 中
 *
 * 使用方式:
 * @PreAuthorize("@perm.check(#projectId, 'issue:create')")
 * @PreAuthorize("@perm.checkGlobal('system:manage_users')")
 *
 * API Key scope 过滤逻辑：
 * 当请求通过 API Key 认证且 scope 非空时，实际权限 = 用户角色权限 ∩ apiKeyScope。
 * scope 为空（[]）表示不限制，保持向后兼容。
 */
@Component("perm")
@RequiredArgsConstructor
public class TrackFlowPermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;

    /**
     * 检查项目级权限
     */
    public boolean check(Long projectId, String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // API Key scope 过滤：如果有 scope 限制且请求的权限不在 scope 中，直接拒绝
        if (!isPermissionInScope(permission)) {
            return false;
        }

        return permissionService.hasPermission(userId, projectId, permission);
    }

    /**
     * 检查全局权限
     */
    public boolean checkGlobal(String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // API Key scope 过滤
        if (!isPermissionInScope(permission)) {
            return false;
        }

        return permissionService.hasGlobalPermission(userId, permission);
    }

    // PermissionEvaluator 接口方法（供 hasPermission SpEL 使用）
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        String perm = permission.toString();
        if (!isPermissionInScope(perm)) {
            return false;
        }

        Long projectId = null;
        if (targetDomainObject instanceof Long) {
            projectId = (Long) targetDomainObject;
        }
        return permissionService.hasPermission(userId, projectId, perm);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        String perm = permission.toString();
        if (!isPermissionInScope(perm)) {
            return false;
        }

        Long projectId = targetId instanceof Long ? (Long) targetId : null;
        return permissionService.hasPermission(userId, projectId, perm);
    }

    /**
     * 判断请求的权限是否在当前 API Key 的 scope 范围内。
     * <p>
     * 规则：
     * - 非 API Key 认证 → 不限制（返回 true）
     * - API Key scope 为空 → 不限制（返回 true，向后兼容）
     * - API Key scope 非空 → permission 必须在 scope 中
     */
    private boolean isPermissionInScope(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken apiKeyToken) {
            if (apiKeyToken.hasScopeRestriction()) {
                return apiKeyToken.getScope().contains(permission);
            }
        }
        return true;
    }
}

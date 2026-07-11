package com.trackflow.auth.security;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 自定义权限评估器：集成到 Spring Security @PreAuthorize 中
 *
 * 使用方式:
 * @PreAuthorize("@perm.check(#projectId, 'issue:create')")
 * @PreAuthorize("@perm.checkGlobal('system:manage_users')")
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
        return permissionService.hasPermission(userId, projectId, permission);
    }

    /**
     * 检查全局权限
     */
    public boolean checkGlobal(String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;
        return permissionService.hasGlobalPermission(userId, permission);
    }

    // PermissionEvaluator 接口方法（供 hasPermission SpEL 使用）
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        Long projectId = null;
        if (targetDomainObject instanceof Long) {
            projectId = (Long) targetDomainObject;
        }
        return permissionService.hasPermission(userId, projectId, permission.toString());
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        Long projectId = targetId instanceof Long ? (Long) targetId : null;
        return permissionService.hasPermission(userId, projectId, permission.toString());
    }
}

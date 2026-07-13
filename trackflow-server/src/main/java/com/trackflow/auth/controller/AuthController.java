package com.trackflow.auth.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.auth.service.UserSyncService;
import com.trackflow.auth.vo.UserInfoVO;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PermissionService permissionService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public R<UserInfoVO> getCurrentUser() {
        Jwt jwt = SecurityUtils.getCurrentJwt();
        if (jwt == null) {
            return R.fail(40100, "未认证");
        }
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String nameClaim = jwt.getClaimAsString("name");
        String username = jwt.getClaimAsString("preferred_username");

        UserInfoVO userInfo = UserInfoVO.builder()
                .keycloakId(jwt.getSubject())
                .username(username)
                .displayName(UserSyncService.buildDisplayName(givenName, familyName, nameClaim, username))
                .email(jwt.getClaimAsString("email"))
                .build();
        return R.ok(userInfo);
    }

    /**
     * 获取当前用户在指定项目中的权限列表
     */
    @GetMapping("/my-permissions")
    @PreAuthorize("isAuthenticated()")
    public R<Set<String>> getMyPermissions(@RequestParam Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Set<String> permissions = new HashSet<>(permissionService.getProjectPermissions(userId, projectId));
        // 合并全局权限
        permissions.addAll(permissionService.getPermissions(userId));
        return R.ok(permissions);
    }

    /**
     * 获取当前用户的全局权限列表（含导航级别的派生权限）
     * 除了全局角色直接分配的权限外，还包含从项目角色聚合的导航级权限：
     * - nav:workflow — 用户在任意项目中拥有 project:manage_workflow 权限
     */
    @GetMapping("/my-global-permissions")
    @PreAuthorize("isAuthenticated()")
    public R<Set<String>> getMyGlobalPermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return R.fail(40100, "未认证");
        }
        Set<String> permissions = new HashSet<>(permissionService.getPermissions(userId));

        // 导航级别的派生权限：项目中有 manage_workflow 权限的用户可见工作流菜单
        if (!permissions.contains("system:admin")) {
            if (permissionService.hasPermissionInAnyProject(userId, "project:manage_workflow")) {
                permissions.add("nav:workflow");
            }
        }

        return R.ok(permissions);
    }
}

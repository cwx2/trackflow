package com.trackflow.auth.controller;

import com.trackflow.auth.security.ApiKeyAuthenticationToken;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.auth.service.UserSyncService;
import com.trackflow.auth.vo.UserInfoVO;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PermissionService permissionService;
    private final ProjectService projectService;
    private final SysUserMapper sysUserMapper;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public R<UserInfoVO> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long dbUserId = SecurityUtils.getCurrentUserId();

        if (auth instanceof ApiKeyAuthenticationToken) {
            // API Key 认证：从数据库获取用户信息
            if (dbUserId == null) {
                return R.fail(ErrorCode.AUTH_MISSING);
            }
            SysUser user = sysUserMapper.selectById(dbUserId);
            if (user == null) {
                return R.fail(ErrorCode.AUTH_MISSING);
            }
            UserInfoVO userInfo = UserInfoVO.builder()
                    .userId(String.valueOf(user.getId()))
                    .keycloakId(user.getKeycloakId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .email(user.getEmail())
                    .authMethod("api_key")
                    .build();
            return R.ok(userInfo);
        }

        // JWT 认证（默认）
        Jwt jwt = SecurityUtils.getCurrentJwt();
        if (jwt == null) {
            return R.fail(ErrorCode.AUTH_MISSING);
        }
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String nameClaim = jwt.getClaimAsString("name");
        String username = jwt.getClaimAsString("preferred_username");

        UserInfoVO userInfo = UserInfoVO.builder()
                .userId(dbUserId != null ? String.valueOf(dbUserId) : null)
                .keycloakId(jwt.getSubject())
                .username(username)
                .displayName(UserSyncService.buildDisplayName(givenName, familyName, nameClaim, username))
                .email(jwt.getClaimAsString("email"))
                .authMethod("jwt")
                .build();
        return R.ok(userInfo);
    }

    /**
     * 获取当前用户在指定项目中的权限列表
     */
    @GetMapping("/my-permissions")
    @PreAuthorize("isAuthenticated()")
    public R<Set<String>> getMyPermissions(@RequestParam("projectId") String projectId) {
        Long resolvedProjectId = projectService.resolveProjectId(projectId);
        Long userId = SecurityUtils.getCurrentUserId();
        Set<String> permissions = new HashSet<>(permissionService.getProjectPermissions(userId, resolvedProjectId));
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
            return R.fail(ErrorCode.AUTH_MISSING);
        }
        Set<String> permissions = new HashSet<>(permissionService.getPermissions(userId));

        // 导航级别的派生权限：项目中有 manage_workflow 权限的用户可见工作流菜单
        if (!permissions.contains("system:admin")) {
            if (permissionService.hasPermissionInAnyProject(userId, "project:manage_workflow")) {
                permissions.add("nav:workflow");
            }
            // 用户在任意项目中有 issue:create 权限 → 显示创建入口
            if (permissionService.hasPermissionInAnyProject(userId, "issue:create")) {
                permissions.add("nav:create_issue");
            }
            // 用户在任意项目中有 report:view 权限 → 显示报表入口
            if (permissionService.hasPermissionInAnyProject(userId, "report:view")) {
                permissions.add("nav:report");
            }
            // 用户在任意项目中有 report:create 权限 → 显示创建报表按钮
            if (permissionService.hasPermissionInAnyProject(userId, "report:create")) {
                permissions.add("nav:report_create");
            }
            // 用户在任意项目中有 sprint:create 权限 → 迭代管理模式（非只读）
            if (permissionService.hasPermissionInAnyProject(userId, "sprint:create")) {
                permissions.add("nav:sprint_manage");
            }
            // 用户在任意项目中有 sprint:view 权限 → 允许前端请求 Sprint 数据
            if (permissionService.hasPermissionInAnyProject(userId, "sprint:view")) {
                permissions.add("nav:sprint_view");
            }
            // 用户在任意项目中有 issue:delete 权限 → 显示回收站入口
            if (permissionService.hasPermissionInAnyProject(userId, "issue:delete")) {
                permissions.add("nav:trash");
            }
            // 用户在任意项目中有批量操作权限 → 显示 checkbox 列和批量工具栏
            if (permissionService.hasPermissionInAnyProject(userId, "issue:edit")
                || permissionService.hasPermissionInAnyProject(userId, "issue:delete")
                || permissionService.hasPermissionInAnyProject(userId, "issue:assign")
                || permissionService.hasPermissionInAnyProject(userId, "issue:change_status")) {
                permissions.add("nav:batch_ops");
            }
        }

        return R.ok(permissions);
    }
}

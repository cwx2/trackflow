package com.trackflow.auth.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.auth.service.UserSyncService;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PermissionService permissionService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getCurrentUser() {
        Jwt jwt = SecurityUtils.getCurrentJwt();
        if (jwt == null) {
            return R.fail(40100, "未认证");
        }
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String nameClaim = jwt.getClaimAsString("name");
        String username = jwt.getClaimAsString("preferred_username");

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("keycloakId", jwt.getSubject());
        userInfo.put("username", username);
        userInfo.put("displayName", UserSyncService.buildDisplayName(givenName, familyName, nameClaim, username));
        userInfo.put("email", jwt.getClaimAsString("email"));
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
     * 获取当前用户的全局权限列表（不含项目级权限）
     */
    @GetMapping("/my-global-permissions")
    @PreAuthorize("isAuthenticated()")
    public R<Set<String>> getMyGlobalPermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return R.fail(40100, "未认证");
        }
        Set<String> permissions = permissionService.getPermissions(userId);
        return R.ok(permissions);
    }
}

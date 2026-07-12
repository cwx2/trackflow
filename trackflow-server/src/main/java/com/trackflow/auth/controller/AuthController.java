package com.trackflow.auth.controller;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
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
    public R<Map<String, Object>> getCurrentUser() {
        Jwt jwt = SecurityUtils.getCurrentJwt();
        if (jwt == null) {
            return R.fail(40100, "未认证");
        }
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("keycloakId", jwt.getSubject());
        userInfo.put("username", jwt.getClaimAsString("preferred_username"));
        userInfo.put("displayName", jwt.getClaimAsString("name"));
        userInfo.put("email", jwt.getClaimAsString("email"));
        return R.ok(userInfo);
    }

    /**
     * 获取当前用户在指定项目中的权限列表
     */
    @GetMapping("/my-permissions")
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
    public R<Set<String>> getMyGlobalPermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        Set<String> permissions = permissionService.getPermissions(userId);
        return R.ok(permissions);
    }
}

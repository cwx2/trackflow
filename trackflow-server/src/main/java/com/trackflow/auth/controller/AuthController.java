package com.trackflow.auth.controller;

import com.trackflow.auth.security.ApiKeyAuthenticationToken;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.auth.service.UserSyncService;
import com.trackflow.auth.vo.UserInfoVO;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.WebUtils;
import com.trackflow.project.service.ProjectService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemAuditService;
import com.trackflow.system.service.UserService;
import com.trackflow.system.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PermissionService permissionService;
    private final ProjectService projectService;
    private final SysUserMapper sysUserMapper;
    private final SystemAuditService systemAuditService;
    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public R<UserInfoVO> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long dbUserId = SecurityUtils.getCurrentUserId();

        if (auth instanceof ApiKeyAuthenticationToken) {
            // API Key 认证：从数据库获取用户信息
            if (dbUserId == null) {
                throw new BusinessException(ErrorCode.AUTH_MISSING);
            }
            SysUser user = sysUserMapper.selectById(dbUserId);
            if (user == null) {
                throw new BusinessException(ErrorCode.AUTH_MISSING);
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
            throw new BusinessException(ErrorCode.AUTH_MISSING);
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
     * 获取当前登录用户的个人资料（包含注册日期等详细信息）
     * 任何已认证用户都可访问自己的资料，无需管理员权限。
     */
    @GetMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public R<UserProfileVO> getMyProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_MISSING);
        }
        UserProfileVO profile = userService.getUserProfile(userId);
        return R.ok(profile);
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
     *
     * 性能优化：结果缓存到 Redis（key: perm:nav:{userId}，TTL 5分钟），
     * 缓存命中时 0 次 DB 查询，未命中时最多 2 次 DB 查询。
     */
    @GetMapping("/my-global-permissions")
    @PreAuthorize("isAuthenticated()")
    public R<Set<String>> getMyGlobalPermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_MISSING);
        }
        return R.ok(permissionService.getNavigationPermissions(userId));
    }

    /**
     * 前端主动登出通知端点。
     * 前端在跳转 Keycloak logout 之前调用此接口，记录 logout 审计事件。
     * <p>
     * 此接口为 best-effort：前端不等待响应（fire-and-forget），
     * 如果调用失败，Keycloak Back-Channel Logout 作为兜底。
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public R<Void> logout(HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return R.ok();
        }

        SysUser user = sysUserMapper.selectById(userId);
        String username = user != null ? user.getUsername() : "unknown";

        systemAuditService.logAuthEvent(
                "logout",
                userId,
                WebUtils.getClientIp(request),
                request.getHeader("User-Agent"),
                Map.of("method", "user_initiated", "username", username)
        );

        log.info("User logout recorded: userId={}, username={}", userId, username);
        return R.ok();
    }
}

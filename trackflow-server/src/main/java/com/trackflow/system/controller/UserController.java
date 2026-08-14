package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.constant.RoleTypes;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.system.converter.UserConverter;
import com.trackflow.system.dto.AssignRoleDTO;
import com.trackflow.system.dto.CreateUserDTO;
import com.trackflow.system.dto.DisableUserDTO;
import com.trackflow.system.dto.UpdateUserRolesDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.service.RoleService;
import com.trackflow.system.service.UserService;
import com.trackflow.system.service.UserVOAssembler;
import com.trackflow.system.vo.UserDataExportVO;
import com.trackflow.system.vo.UserDetailVO;
import com.trackflow.system.vo.UserProfileVO;
import com.trackflow.system.vo.UserPublicProfileVO;
import com.trackflow.system.vo.UserSummaryVO;
import com.trackflow.system.vo.UserStatsVO;
import com.trackflow.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserVOAssembler userVOAssembler;
    private final RoleService roleService;
    private final UserConverter userConverter;

    @GetMapping("/stats")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserStatsVO> stats() {
        return R.ok(userService.getStats());
    }

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserVO> create(@Valid @RequestBody CreateUserDTO dto) {
        SysUser user = userService.createUser(dto);
        return R.ok(userConverter.toVO(user));
    }

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<PageResult<UserVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "banStatus", required = false) String banStatus,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "sort", required = false) String sort) {

        Page<SysUser> pageObj = PageHelper.buildPage(page, pageSize, sort,
                Set.of("id", "username", "display_name", "email", "status",
                        "org_id", "created_at", "updated_at", "last_login_at"));
        PageResult<UserVO> result = userVOAssembler.listUsersWithRoles(
                pageObj, keyword, username, displayName, email, orgId, status, banStatus, roleId);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserDetailVO> getById(@PathVariable("id") Long id) {
        return R.ok(userVOAssembler.getUserDetail(id));
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserProfileVO> getProfile(@PathVariable("id") Long id) {
        UserProfileVO profile = userVOAssembler.getUserProfile(id);
        return R.ok(profile);
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> disable(@PathVariable("id") Long id, @Valid @RequestBody DisableUserDTO dto) {
        userService.disable(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> enable(@PathVariable("id") Long id) {
        userService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> assignRole(@PathVariable("id") Long id, @Valid @RequestBody AssignRoleDTO dto) {
        userService.assignGlobalRole(id, dto.getRoleId());
        return R.ok();
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> removeRole(@PathVariable("id") Long id, @PathVariable("roleId") Long roleId) {
        userService.removeGlobalRole(id, roleId);
        return R.ok();
    }

    /**
     * 批量替换用户的全局角色集合
     * 
     * 语义：传入期望的完整角色 ID 列表，服务端计算差异后执行增删
     * - 传入 [1, 2] 表示用户最终拥有角色 1 和 2
     * - 传入 [] 表示清空所有全局角色
     * 
     * @param id 用户 ID
     * @param dto 包含期望角色 ID 列表的 DTO
     * @return 操作结果
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<Void> replaceRoles(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRolesDTO dto) {
        userService.replaceGlobalRoles(id, dto.getRoleIds());
        return R.ok();
    }

    /**
     * 导出指定用户的所有个人数据
     * 用于满足 GDPR「数据可携权」（Right to Data Portability）要求
     *
     * @param id 用户ID
     * @return 用户的完整个人数据
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserDataExportVO> exportUserData(@PathVariable("id") Long id) {
        UserDataExportVO exportData = userVOAssembler.exportUserData(id);
        return R.ok(exportData);
    }

    /**
     * 获取用户摘要信息（头像、姓名、用户名）
     * 用于悬停卡片等轻量级展示场景，所有认证用户可访问
     *
     * @param id 用户ID
     * @return 用户摘要
     */
    @GetMapping("/{id}/summary")
    @PreAuthorize("isAuthenticated()")
    public R<UserSummaryVO> getSummary(@PathVariable("id") Long id) {
        SysUser user = userService.getById(id);
        return R.ok(userConverter.toSummaryVO(user));
    }

    /**
     * 获取用户公开资料（权限分级）
     *
     * 权限逻辑：
     * - 系统管理员 → 完整信息
     * - 有共同项目 → 基础公开信息
     * - 无共同项目 → 403
     *
     * @param id 目标用户ID
     * @return 公开资料（字段根据权限过滤）
     */
    @GetMapping("/{id}/public-profile")
    @PreAuthorize("isAuthenticated()")
    public R<UserPublicProfileVO> getPublicProfile(@PathVariable("id") Long id) {
        Long requesterId = SecurityUtils.getCurrentUserId();
        UserPublicProfileVO profile = userVOAssembler.getUserPublicProfile(id, requesterId);
        return R.ok(profile);
    }
}

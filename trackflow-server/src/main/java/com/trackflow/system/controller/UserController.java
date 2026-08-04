package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.converter.UserConverter;
import com.trackflow.system.dto.AssignRoleDTO;
import com.trackflow.system.dto.CreateUserDTO;
import com.trackflow.system.dto.DisableUserDTO;
import com.trackflow.system.dto.UpdateUserRolesDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.service.RoleService;
import com.trackflow.system.service.UserService;
import com.trackflow.system.vo.UserDataExportVO;
import com.trackflow.system.vo.UserDetailVO;
import com.trackflow.system.vo.UserProfileVO;
import com.trackflow.system.vo.UserSummaryVO;
import com.trackflow.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final UserConverter userConverter;

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
        Page<SysUser> result = userService.list(pageObj, keyword, username, displayName, email, orgId, status, banStatus, roleId);

        List<UserVO> voList = userConverter.toVOList(result.getRecords());

        // 批量填充全局角色信息（避免 N+1）
        if (!result.getRecords().isEmpty()) {
            List<Long> userIds = result.getRecords().stream().map(SysUser::getId).toList();
            Map<Long, List<UserVO.GlobalRoleInfo>> rolesMap = userService.batchGetGlobalRoles(userIds);
            for (int i = 0; i < voList.size(); i++) {
                Long userId = result.getRecords().get(i).getId();
                voList.get(i).setGlobalRoles(rolesMap.getOrDefault(userId, List.of()));
            }
        }

        PageResult<UserVO> pageResult = new PageResult<>(
                voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserDetailVO> getById(@PathVariable("id") Long id) {
        SysUser user = userService.getById(id);
        List<Long> roleIds = userService.getUserGlobalRoleIds(id);

        UserDetailVO detail = new UserDetailVO();
        detail.setUser(userConverter.toVO(user));
        detail.setRoleIds(roleIds.stream().map(String::valueOf).toList());
        return R.ok(detail);
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("@perm.checkGlobal('system:manage_users')")
    public R<UserProfileVO> getProfile(@PathVariable("id") Long id) {
        UserProfileVO profile = userService.getUserProfile(id);
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
        Long roleId = dto.getRoleId();

        // 检查角色类型必须是 global
        SysRole role = roleService.getById(roleId);
        if (!"global".equals(role.getRoleType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "此处只能分配全局角色，项目角色请通过项目成员管理进行分配");
        }

        userService.assignGlobalRole(id, roleId);
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
        UserDataExportVO exportData = userService.exportUserData(id);
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
}

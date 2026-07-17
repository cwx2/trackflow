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
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.service.RoleService;
import com.trackflow.system.service.UserService;
import com.trackflow.system.vo.UserDetailVO;
import com.trackflow.system.vo.UserProfileVO;
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
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "orgId", required = false) Long orgId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "sort", required = false) String sort) {

        Page<SysUser> pageObj = PageHelper.buildPage(page, pageSize, sort,
                Set.of("id", "username", "display_name", "email", "status",
                        "org_id", "created_at", "updated_at", "last_login_at"));
        Page<SysUser> result = userService.list(pageObj, username, displayName, email, orgId, status);

        PageResult<UserVO> pageResult = new PageResult<>(
                userConverter.toVOList(result.getRecords()), result.getTotal(),
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
    public R<Void> disable(@PathVariable("id") Long id) {
        userService.disable(id);
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
                    "Only global roles can be assigned here. Project roles are assigned via project membership.");
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
}

package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.converter.RoleConverter;
import com.trackflow.system.dto.CloneRoleDTO;
import com.trackflow.system.dto.CreateRoleDTO;
import com.trackflow.system.dto.MergeRolesDTO;
import com.trackflow.system.dto.ReplacePermissionsDTO;
import com.trackflow.system.dto.UpdateRoleDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.PermissionImplication;
import com.trackflow.system.service.PermissionImplicationService;
import com.trackflow.system.service.RoleService;
import com.trackflow.system.vo.PermissionGroupVO;
import com.trackflow.system.vo.RoleStatsVO;
import com.trackflow.system.vo.RoleUsersVO;
import com.trackflow.system.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("@perm.checkGlobal('system:manage_roles')")
public class RoleController {

    private final RoleService roleService;
    private final RoleConverter roleConverter;
    private final PermissionImplicationService permissionImplicationService;

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> create(@Valid @RequestBody CreateRoleDTO dto) {
        return R.ok(roleConverter.toVO(roleService.create(dto)));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> clone(@PathVariable("id") Long id, @Valid @RequestBody CloneRoleDTO dto) {
        return R.ok(roleConverter.toVO(roleService.clone(id, dto.getName(), dto.getCode())));
    }

    /**
     * 合并角色 — 将多个源角色合并到一个目标角色。
     * <p>
     * 参考 YouTrack Merge Roles 功能：
     * - 目标角色继承所有源角色的权限（并集）
     * - 所有源角色的用户/组分配被迁移到目标角色
     * - 源角色被删除
     * - 内置角色（builtin=true）不可作为源角色被合并删除
     * </p>
     */
    @PostMapping("/merge")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> merge(@Valid @RequestBody MergeRolesDTO dto) {
        return R.ok(roleConverter.toVO(
                roleService.mergeRoles(dto.getSourceRoleIds(), dto.getTargetRoleId())));
    }

    @GetMapping("/stats")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleStatsVO> stats() {
        return R.ok(roleService.getStats());
    }

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<PageResult<RoleVO>> list(
            @RequestParam(value = "roleType", required = false) String roleType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        Page<SysRole> pageObj = PageHelper.buildPage(page, pageSize);
        Page<SysRole> result = roleService.list(pageObj, roleType);

        List<RoleVO> voList = roleConverter.toVOList(result.getRecords());

        // 批量填充用户计数
        List<Long> roleIds = result.getRecords().stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        Map<Long, Integer> userCounts = roleService.getUserCountsByRoleIds(roleIds);
        for (RoleVO vo : voList) {
            Long roleId = Long.valueOf(vo.getId());
            vo.setUserCount(userCounts.getOrDefault(roleId, 0));
        }

        PageResult<RoleVO> pageResult = new PageResult<>(
                voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> getById(@PathVariable("id") Long id) {
        return R.ok(roleConverter.toVO(roleService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateRoleDTO dto) {
        return R.ok(roleConverter.toVO(roleService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> setEnabled(@PathVariable("id") Long id, @RequestParam boolean enabled) {
        return R.ok(roleConverter.toVO(roleService.setEnabled(id, enabled)));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> replacePermissions(@PathVariable("id") Long id, @Valid @RequestBody ReplacePermissionsDTO dto) {
        roleService.replacePermissions(id, dto.getPermissions());
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<String>> getPermissions(@PathVariable("id") Long id) {
        return R.ok(roleService.getPermissions(id));
    }

    /**
     * 获取角色已分配用户列表（全局角色直接列出用户，项目角色按项目分组）
     */
    @GetMapping("/{id}/users")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleUsersVO> getRoleUsers(@PathVariable("id") Long id) {
        return R.ok(roleService.getRoleUsers(id));
    }

    @GetMapping("/all-permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Map<String, List<String>>> getAllPermissions() {
        return R.ok(roleService.getAllPermissions());
    }

    /**
     * 获取当前操作者持有的所有权限（用于前端提权保护 UI）。
     * 返回操作者的全局权限 + 所有项目级权限的合集。
     * system_admin 返回 ["*"] 表示拥有所有权限。
     */
    @GetMapping("/my-grantable-permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Set<String>> getMyGrantablePermissions() {
        return R.ok(roleService.getGrantablePermissions());
    }

    /**
     * 获取所有权限定义（含分类、名称、描述等元数据）
     */
    @GetMapping("/permission-definitions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<PermissionGroupVO>> getPermissionDefinitions() {
        return R.ok(roleService.getAllPermissionGroups());
    }

    /**
     * 获取所有权限隐含关系（用于前端权限分配 UI 自动勾选/取消）
     */
    @GetMapping("/permission-implications")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<PermissionImplication>> getPermissionImplications() {
        return R.ok(permissionImplicationService.getAllImplications());
    }
}

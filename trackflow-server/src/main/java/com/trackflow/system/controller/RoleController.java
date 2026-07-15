package com.trackflow.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.PageHelper;
import com.trackflow.system.converter.RoleConverter;
import com.trackflow.system.dto.CreateRoleDTO;
import com.trackflow.system.dto.UpdateRoleDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.service.RoleService;
import com.trackflow.system.vo.PermissionGroupVO;
import com.trackflow.system.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RoleConverter roleConverter;

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> create(@Valid @RequestBody CreateRoleDTO dto) {
        return R.ok(roleConverter.toVO(roleService.create(dto)));
    }

    @GetMapping
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<PageResult<RoleVO>> list(
            @RequestParam(value = "roleType", required = false) String roleType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        Page<SysRole> pageObj = PageHelper.buildPage(page, pageSize);
        Page<SysRole> result = roleService.list(pageObj, roleType);

        PageResult<RoleVO> pageResult = new PageResult<>(
                roleConverter.toVOList(result.getRecords()), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> getById(@PathVariable Long id) {
        return R.ok(roleConverter.toVO(roleService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<RoleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleDTO dto) {
        return R.ok(roleConverter.toVO(roleService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Void> replacePermissions(@PathVariable Long id, @RequestBody List<String> permissions) {
        roleService.replacePermissions(id, permissions);
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<String>> getPermissions(@PathVariable Long id) {
        return R.ok(roleService.getPermissions(id));
    }

    @GetMapping("/all-permissions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<Map<String, List<String>>> getAllPermissions() {
        return R.ok(roleService.getAllPermissions());
    }

    /**
     * 获取所有权限定义（含分类、名称、描述等元数据）
     */
    @GetMapping("/permission-definitions")
    @PreAuthorize("@perm.checkGlobal('system:manage_roles')")
    public R<List<PermissionGroupVO>> getPermissionDefinitions() {
        return R.ok(roleService.getAllPermissionGroups());
    }
}

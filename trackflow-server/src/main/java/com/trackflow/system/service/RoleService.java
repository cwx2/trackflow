package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.system.dto.CreateRoleDTO;
import com.trackflow.system.dto.UpdateRoleDTO;
import com.trackflow.system.entity.RolePermission;
import com.trackflow.system.entity.SysPermission;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.RolePermissionMapper;
import com.trackflow.system.mapper.SysPermissionMapper;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import com.trackflow.system.vo.PermissionGroupVO;
import com.trackflow.system.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionService permissionService;
    private final SystemAuditService systemAuditService;

    @Transactional
    public SysRole create(CreateRoleDTO dto) {
        // 检查 code 唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_DUPLICATE);
        }

        SysRole role = new SysRole();
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setDescription(dto.getDescription());
        role.setRoleType(dto.getRoleType());
        role.setBuiltin(false);
        role.setSortOrder(0);
        roleMapper.insert(role);
        return role;
    }

    public Page<SysRole> list(Page<SysRole> page, String roleType) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (roleType != null && !roleType.isBlank()) {
            wrapper.eq(SysRole::getRoleType, roleType);
        }
        wrapper.orderByAsc(SysRole::getSortOrder);
        return roleMapper.selectPage(page, wrapper);
    }

    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found");
        }
        return role;
    }

    @Transactional
    public SysRole update(Long id, UpdateRoleDTO dto) {
        SysRole role = getById(id);
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "角色名称不能为空");
            }
            role.setName(trimmedName);
        }
        if (dto.getDescription() != null) role.setDescription(dto.getDescription().trim());
        if (dto.getSortOrder() != null) role.setSortOrder(dto.getSortOrder());
        roleMapper.updateById(role);
        return role;
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = getById(id);

        // 内置角色不能删除
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED);
        }

        // 检查是否有用户关联
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }

        // 删除角色及其权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        );
        roleMapper.deleteById(id);
    }

    /**
     * 克隆角色（复制角色定义 + 权限）
     */
    @Transactional
    public SysRole clone(Long sourceId, String newName, String newCode) {
        SysRole source = getById(sourceId);

        // 检查新编码唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, newCode)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_DUPLICATE);
        }

        // 创建新角色（继承类型，标记为非内置）
        SysRole newRole = new SysRole();
        newRole.setName(newName);
        newRole.setCode(newCode);
        newRole.setDescription(source.getDescription());
        newRole.setRoleType(source.getRoleType());
        newRole.setBuiltin(false);
        newRole.setSortOrder(0);
        roleMapper.insert(newRole);

        // 复制权限
        List<RolePermission> sourcePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, sourceId)
        );
        for (RolePermission sp : sourcePerms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(newRole.getId());
            rp.setPermission(sp.getPermission());
            rolePermissionMapper.insert(rp);
        }

        // 审计日志
        systemAuditService.log("clone_role", "role", newRole.getId(),
                Map.of("sourceName", source.getName(),
                        "sourceId", source.getId(),
                        "newName", newName,
                        "newCode", newCode,
                        "permissionCount", sourcePerms.size()));

        return newRole;
    }

    /**
     * 替换角色的所有权限
     */
    @Transactional
    public void replacePermissions(Long id, List<String> permissions) {
        SysRole role = getById(id); // 确保存在

        // 记录旧权限（审计用）
        List<String> oldPermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        ).stream().map(RolePermission::getPermission).collect(Collectors.toList());

        // 删除旧权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        );

        // 插入新权限
        for (String perm : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermission(perm);
            rolePermissionMapper.insert(rp);
        }

        // 失效缓存
        permissionService.invalidateCacheForRole(id);

        // 审计日志：记录旧权限和新权限
        systemAuditService.log("update_role_permissions", "role", id,
                Map.of("roleName", role.getName(),
                        "oldPermissions", oldPermissions,
                        "newPermissions", permissions));
    }

    /**
     * 获取角色的权限列表
     */
    public List<String> getPermissions(Long id) {
        getById(id); // 确保存在
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        ).stream().map(RolePermission::getPermission).collect(Collectors.toList());
    }

    /**
     * 获取所有可用权限（按分类分组，含元数据）
     */
    public List<PermissionGroupVO> getAllPermissionGroups() {
        List<SysPermission> allPerms = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getEnabled, true)
                        .orderByAsc(SysPermission::getCategory)
                        .orderByAsc(SysPermission::getSortOrder)
        );

        // 按 category 分组，保持插入顺序
        Map<String, List<PermissionVO>> grouped = new LinkedHashMap<>();
        for (SysPermission perm : allPerms) {
            PermissionVO vo = new PermissionVO();
            vo.setCode(perm.getCode());
            vo.setName(perm.getName());
            vo.setDescription(perm.getDescription());
            vo.setScope(perm.getScope());
            grouped.computeIfAbsent(perm.getCategory(), k -> new ArrayList<>()).add(vo);
        }

        // 转为 List<PermissionGroupVO>
        List<PermissionGroupVO> result = new ArrayList<>();
        for (Map.Entry<String, List<PermissionVO>> entry : grouped.entrySet()) {
            PermissionGroupVO group = new PermissionGroupVO();
            group.setCategory(entry.getKey());
            group.setPermissions(entry.getValue());
            result.add(group);
        }
        return result;
    }

    /**
     * 获取所有可用权限（按分类，兼容旧格式：Map<category, List<code>>）
     */
    public Map<String, List<String>> getAllPermissions() {
        List<SysPermission> allPerms = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getEnabled, true)
                        .orderByAsc(SysPermission::getCategory)
                        .orderByAsc(SysPermission::getSortOrder)
        );

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (SysPermission perm : allPerms) {
            result.computeIfAbsent(perm.getCategory(), k -> new ArrayList<>()).add(perm.getCode());
        }
        return result;
    }
}

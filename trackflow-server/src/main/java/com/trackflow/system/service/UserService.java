package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Long SYSTEM_ADMIN_ROLE_ID = 1L;

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    /**
     * 分页查询用户列表
     */
    public Page<SysUser> list(Page<SysUser> page, String username, String displayName,
                              String email, Long orgId, String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (displayName != null && !displayName.isBlank()) {
            wrapper.like(SysUser::getDisplayName, displayName);
        }
        if (email != null && !email.isBlank()) {
            wrapper.like(SysUser::getEmail, email);
        }
        if (orgId != null) {
            wrapper.eq(SysUser::getOrgId, orgId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        return userMapper.selectPage(page, wrapper);
    }

    /**
     * 获取用户详情
     */
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }
        return user;
    }

    /**
     * 获取用户的全局角色 ID 列表
     */
    public List<Long> getUserGlobalRoleIds(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).toList();
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disable(Long id) {
        SysUser user = getById(id);

        // 保护最后一个系统管理员
        if (isLastSystemAdmin(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot disable the last system administrator");
        }

        user.setStatus("disabled");
        userMapper.updateById(user);
        permissionService.invalidateCache(id);
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enable(Long id) {
        SysUser user = getById(id);
        user.setStatus("active");
        userMapper.updateById(user);
    }

    /**
     * 分配全局角色
     */
    @Transactional
    public void assignGlobalRole(Long userId, Long roleId) {
        // 检查是否已存在
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId)
        );
        if (count > 0) return;

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);
        permissionService.invalidateCache(userId);
    }

    /**
     * 移除全局角色
     */
    @Transactional
    public void removeGlobalRole(Long userId, Long roleId) {
        // 保护最后一个系统管理员
        if (SYSTEM_ADMIN_ROLE_ID.equals(roleId) && isLastSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot remove the last system administrator role");
        }

        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId)
        );
        permissionService.invalidateCache(userId);
    }

    /**
     * 检查用户是否是最后一个系统管理员
     */
    private boolean isLastSystemAdmin(Long userId) {
        // 查看有多少用户拥有系统管理员角色
        List<Long> adminUserIds = userRoleMapper.selectUserIdsByRoleId(SYSTEM_ADMIN_ROLE_ID);
        return adminUserIds.size() == 1 && adminUserIds.contains(userId);
    }
}

package com.trackflow.auth.service;

import com.trackflow.system.mapper.RolePermissionMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务：Redis 缓存 + 数据库查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String CACHE_KEY_PREFIX = "perm:user:";
    private static final String PROJECT_CACHE_KEY_PREFIX = "perm:user:project:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    private final StringRedisTemplate redisTemplate;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * 检查用户是否拥有指定权限（全局 + 项目级）
     */
    public boolean hasPermission(Long userId, Long projectId, String permission) {
        if (userId == null) return false;

        // 先检查全局权限
        Set<String> globalPerms = getPermissions(userId);
        if (globalPerms.contains(SYSTEM_ADMIN_PERMISSION)) {
            return true;
        }
        if (globalPerms.contains(permission)) {
            return true;
        }

        // 检查项目级权限
        if (projectId != null) {
            Set<String> projectPerms = getProjectPermissions(userId, projectId);
            return projectPerms.contains(permission);
        }

        return false;
    }

    /**
     * 检查用户是否拥有全局权限
     */
    public boolean hasGlobalPermission(Long userId, String permission) {
        return hasPermission(userId, null, permission);
    }

    /**
     * 检查用户是否是系统管理员
     */
    public boolean isSystemAdmin(Long userId) {
        Set<String> permissions = getPermissions(userId);
        return permissions.contains(SYSTEM_ADMIN_PERMISSION);
    }

    /**
     * 获取用户所有权限（缓存优先）
     */
    public Set<String> getPermissions(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;

        // 1. 缓存查询
        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 2. 数据库查询
        Set<String> permissions = loadPermissionsFromDb(userId);

        // 3. 写入缓存
        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
            redisTemplate.expire(cacheKey, CACHE_TTL);
        }

        return permissions;
    }

    /**
     * 失效指定用户的权限缓存
     */
    public void invalidateCache(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        log.debug("Permission cache invalidated for user {}", userId);
    }

    /**
     * 失效拥有指定角色的所有用户的权限缓存
     */
    public void invalidateCacheForRole(Long roleId) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
        for (Long userId : userIds) {
            invalidateCache(userId);
        }
        log.debug("Permission cache invalidated for {} users of role {}", userIds.size(), roleId);
    }

    /**
     * 从数据库加载用户权限
     */
    private Set<String> loadPermissionsFromDb(Long userId) {
        List<String> permissions = rolePermissionMapper.selectPermissionsByUserId(userId);
        return new HashSet<>(permissions);
    }

    /**
     * 获取用户在指定项目中的权限（缓存优先）
     */
    public Set<String> getProjectPermissions(Long userId, Long projectId) {
        String cacheKey = PROJECT_CACHE_KEY_PREFIX + userId + ":" + projectId;

        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 从数据库加载：project_member → role_permission
        Set<String> permissions = loadProjectPermissionsFromDb(userId, projectId);

        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
            redisTemplate.expire(cacheKey, CACHE_TTL);
        }

        return permissions;
    }

    /**
     * 从数据库加载用户在项目中的权限
     */
    private Set<String> loadProjectPermissionsFromDb(Long userId, Long projectId) {
        List<String> permissions = rolePermissionMapper.selectPermissionsByUserAndProject(userId, projectId);
        return new HashSet<>(permissions);
    }
}

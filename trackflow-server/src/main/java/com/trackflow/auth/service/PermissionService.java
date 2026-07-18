package com.trackflow.auth.service;

import com.trackflow.issue.entity.Issue;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.system.mapper.RolePermissionMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 权限服务：Redis 缓存 + 数据库查询
 * <p>
 * 支持 NonMember / Anonymous 访问控制：
 * - 如果用户不是项目成员，检查项目 visibility
 * - internal 项目：已登录用户获得 NonMember 角色权限
 * - public 项目：所有人获得 Anonymous 角色权限（已登录用户获得 NonMember 权限）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String CACHE_KEY_PREFIX = "perm:user:";
    private static final String PROJECT_CACHE_KEY_PREFIX = "perm:user:project:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    /** NonMember 内置角色 ID（登录但非成员访问 internal/public 项目） */
    private static final Long NON_MEMBER_ROLE_ID = 8L;
    /** Anonymous 内置角色 ID（未登录访问 public 项目） */
    private static final Long ANONYMOUS_ROLE_ID = 9L;

    private final StringRedisTemplate redisTemplate;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final ProjectMapper projectMapper;

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
     * 失效指定用户的所有权限缓存（全局 + 所有项目级）
     * 使用 SCAN 迭代匹配项目级 key，避免 KEYS 命令阻塞 Redis
     */
    public void invalidateCache(Long userId) {
        // 1. 删除全局权限缓存
        String globalKey = CACHE_KEY_PREFIX + userId;
        redisTemplate.delete(globalKey);

        // 2. 使用 SCAN 迭代删除该用户所有项目级权限缓存
        String projectPattern = PROJECT_CACHE_KEY_PREFIX + userId + ":*";
        Set<String> projectKeys = scanKeys(projectPattern);
        if (!projectKeys.isEmpty()) {
            redisTemplate.delete(projectKeys);
            log.debug("Permission cache invalidated for user {}: global + {} project keys", userId, projectKeys.size());
        } else {
            log.debug("Permission cache invalidated for user {}: global only", userId);
        }
    }

    /**
     * 使用 SCAN 命令迭代匹配 Redis key（非阻塞，生产安全）
     * 每次迭代扫描 100 个 key，避免长时间阻塞 Redis
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
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
     *
     * 逻辑：
     * 1. 先查成员角色权限 → 有则返回
     * 2. 无成员关系时，查项目 visibility
     * 3. visibility = internal/public 且用户已登录 → 返回 NonMember 角色权限
     * 4. visibility = public 且未登录 → 返回 Anonymous 角色权限
     */
    public Set<String> getProjectPermissions(Long userId, Long projectId) {
        String cacheKey = PROJECT_CACHE_KEY_PREFIX + userId + ":" + projectId;

        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 从数据库加载：project_member → role_permission
        Set<String> permissions = loadProjectPermissionsFromDb(userId, projectId);

        // 如果用户不是成员，尝试 NonMember fallback
        if (permissions.isEmpty() && userId != null) {
            permissions = loadNonMemberPermissions(projectId);
        }

        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
            redisTemplate.expire(cacheKey, CACHE_TTL);
        }

        return permissions;
    }

    /**
     * 获取匿名用户（未登录）在指定项目中的权限。
     * 仅当项目 visibility = public 时返回 Anonymous 角色权限。
     *
     * @apiNote 当前 SecurityConfig 要求所有 /api/v1/** 接口 authenticated()，
     * 匿名访问需要后续开放部分 endpoint 为 permitAll 时使用此方法。
     * 预留接口，供 Anonymous 请求链路实现时调用。
     */
    public Set<String> getAnonymousProjectPermissions(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return Set.of();
        }
        if (ProjectVisibility.PUBLIC == project.getVisibility()) {
            return loadRolePermissions(ANONYMOUS_ROLE_ID);
        }
        return Set.of();
    }

    /**
     * 当用户不是项目成员时，根据项目可见性返回 NonMember 角色权限。
     * - visibility = internal 或 public → 返回 NonMember 权限
     * - visibility = private → 返回空集
     */
    private Set<String> loadNonMemberPermissions(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return Set.of();
        }
        ProjectVisibility visibility = project.getVisibility();
        if (ProjectVisibility.INTERNAL == visibility || ProjectVisibility.PUBLIC == visibility) {
            return loadRolePermissions(NON_MEMBER_ROLE_ID);
        }
        return Set.of();
    }

    /**
     * 加载指定角色的所有权限
     */
    private Set<String> loadRolePermissions(Long roleId) {
        List<String> permissions = rolePermissionMapper.selectPermissionsByRoleId(roleId);
        return new HashSet<>(permissions);
    }

    /**
     * 从数据库加载用户在项目中的权限
     */
    private Set<String> loadProjectPermissionsFromDb(Long userId, Long projectId) {
        List<String> permissions = rolePermissionMapper.selectPermissionsByUserAndProject(userId, projectId);
        return new HashSet<>(permissions);
    }

    /**
     * 检查用户是否在任何项目中拥有指定权限
     * 用于导航级别的权限判断（如：用户在任何项目中是否可以管理工作流）
     */
    public boolean hasPermissionInAnyProject(Long userId, String permission) {
        if (userId == null) return false;
        // system:admin 拥有所有权限
        if (isSystemAdmin(userId)) return true;
        return rolePermissionMapper.hasPermissionInAnyProject(userId, permission);
    }

    /**
     * 检查用户对特定 Issue 的权限（含资源级规则）。
     *
     * 资源级规则：
     * - Issue 的 reporter（创建者）自动获得 issue:edit 权限
     * - Issue 的 assignee（负责人）自动获得 issue:edit 和 issue:change_status 权限
     *
     * @param userId     当前操作用户
     * @param issue      目标 Issue 对象
     * @param permission 要检查的权限码
     * @return true 如果用户有权限
     */
    public boolean hasIssuePermission(Long userId, Issue issue, String permission) {
        if (userId == null || issue == null) return false;

        // 1. 项目级权限直接满足（含 system:admin 检查）
        if (hasPermission(userId, issue.getProjectId(), permission)) {
            return true;
        }

        // 2. 资源级规则：创建者（reporter）可编辑
        if ("issue:edit".equals(permission) && Objects.equals(userId, issue.getReporterId())) {
            return true;
        }

        // 3. 资源级规则：负责人（assignee）可编辑
        if ("issue:edit".equals(permission) && Objects.equals(userId, issue.getAssigneeId())) {
            return true;
        }

        // 4. 资源级规则：负责人可变更状态
        if ("issue:change_status".equals(permission) && Objects.equals(userId, issue.getAssigneeId())) {
            return true;
        }

        return false;
    }
}

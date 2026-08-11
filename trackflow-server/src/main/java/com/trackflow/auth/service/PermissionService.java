package com.trackflow.auth.service;

import com.trackflow.auth.security.ApiKeyAuthenticationToken;
import com.trackflow.issue.entity.Issue;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.project.service.ProjectModuleService;
import com.trackflow.system.mapper.GlobalMemberMapper;
import com.trackflow.system.mapper.RolePermissionMapper;
import com.trackflow.system.mapper.SysPermissionMapper;
import com.trackflow.system.mapper.UserGroupRoleMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务：Redis 缓存 + 数据库查询 + 模块过滤
 * <p>
 * 支持三层权限检查：
 * 1. 角色 → 权限（role_permission 表）
 * 2. 项目启用模块 → 权限过滤（project_enabled_module + sys_permission.category）
 * 3. 资源级规则（reporter/assignee 特殊权限）
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
    private static final String NAV_CACHE_KEY_PREFIX = "perm:nav:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String SYSTEM_ADMIN_PERMISSION = "system:admin";

    /**
     * 空权限缓存占位符。
     * 当用户在某个项目/全局中确实没有任何权限时，将此占位符写入 Redis Set，
     * 防止空结果穿透——下次请求命中此占位符即可直接返回空集合，不再查 DB。
     * 使用 "__" 前缀确保不会与任何合法权限码冲突。
     */
    private static final String EMPTY_PERMISSIONS_PLACEHOLDER = "__NO_PERMISSIONS__";

    /** NonMember 内置角色 ID（登录但非成员访问 internal/public 项目） */
    private static final Long NON_MEMBER_ROLE_ID = 8L;
    /** Anonymous 内置角色 ID（未登录访问 public 项目） */
    private static final Long ANONYMOUS_ROLE_ID = 9L;

    /**
     * Reporter 固有权限集合（Inherent Permissions）。
     * 参考 YouTrack：Issue reporter 永远有权查看、编辑、评论自己创建的工单，
     * 即使角色中没有显式授予 Read Issue / Update Issue / Add Comment 权限。
     * 这是基于资源所有权的无条件自动授权，与 issue:edit_own 等可配置权限不同。
     * 注意：不包含 issue:change_status，状态转换需通过工作流引擎控制，需要明确的角色权限授权。
     */
    private static final Set<String> REPORTER_INHERENT_PERMISSIONS = Set.of(
            "issue:view",
            "issue:edit",
            "issue:comment"
    );

    /**
     * Assignee 固有权限集合。
     * 负责人对分配给自己的工单天然继承 view 权限。
     * 编辑和状态变更仍需 issue:edit_assigned 权限（可配置的资源级规则）。
     */
    private static final Set<String> ASSIGNEE_INHERENT_PERMISSIONS = Set.of(
            "issue:view"
    );

    private final StringRedisTemplate redisTemplate;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserGroupRoleMapper userGroupRoleMapper;
    private final ProjectMapper projectMapper;
    private final GlobalMemberMapper globalMemberMapper;
    private final ProjectModuleService projectModuleService;
    private final SysPermissionMapper sysPermissionMapper;

    /**
     * 权限码 → 所属模块（category）映射。
     * 启动时从 sys_permission 表加载，内存常驻（权限定义极少变化）。
     * Key: permission code (e.g. "time:log"), Value: category (e.g. "time_tracking")
     */
    private final Map<String, String> permissionCategoryMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadPermissionCategoryMap();
    }

    /**
     * 加载权限码→模块映射（从 sys_permission 表）
     */
    private void loadPermissionCategoryMap() {
        List<Map<String, String>> rows = sysPermissionMapper.selectProjectPermissionCategories();
        permissionCategoryMap.clear();
        for (Map<String, String> row : rows) {
            permissionCategoryMap.put(row.get("code"), row.get("category"));
        }
        log.info("Loaded {} permission→category mappings for module filtering", permissionCategoryMap.size());
    }

    /**
     * 刷新权限→模块映射缓存（当 sys_permission 表变化时调用）
     */
    public void refreshPermissionCategoryMap() {
        loadPermissionCategoryMap();
    }

    /**
     * 检查用户是否拥有指定权限（全局 + 项目级）。
     * <p>
     * 内置 API Key scope 过滤：当请求通过 API Key 认证且 scope 非空时，
     * 实际权限 = 用户角色权限 ∩ apiKeyScope。无论调用方是 Controller 层
     * 的 @PreAuthorize 还是 Service 层的直接调用，scope 过滤都不可绕过。
     * <p>
     * system:admin 处理规则：
     * - 如果 API Key scope 包含 "system:admin"，则允许所有操作（管理员不受限制）
     * - 如果 API Key scope 不包含 "system:admin"，即使用户是管理员也必须按 scope 限制执行
     */
    public boolean hasPermission(Long userId, Long projectId, String permission) {
        if (userId == null) return false;

        // API Key scope 过滤：如果有 scope 限制且请求的权限不在 scope 中，直接拒绝
        if (!isPermissionInCurrentScope(permission)) {
            return false;
        }

        // 先检查全局权限
        Set<String> globalPerms = getPermissions(userId);
        if (globalPerms.contains(SYSTEM_ADMIN_PERMISSION)) {
            // system:admin 用户——如果 scope 允许 system:admin 或无 scope 限制，则放行一切
            // 如果 scope 不含 system:admin（如只含 issue:view），则管理员身份不生效，
            // 但上面 isPermissionInCurrentScope(permission) 已通过，继续检查具体权限
            if (isPermissionInCurrentScope(SYSTEM_ADMIN_PERMISSION)) {
                return true;
            }
            // scope 不含 system:admin，但含所请求的 permission → 继续检查角色权限
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
     * 检查用户是否是系统管理员。
     * <p>
     * 内置 API Key scope 过滤：即使用户拥有 system:admin 角色权限，
     * 如果 API Key scope 中没有 "system:admin"，也返回 false。
     */
    public boolean isSystemAdmin(Long userId) {
        if (!isPermissionInCurrentScope(SYSTEM_ADMIN_PERMISSION)) {
            return false;
        }
        Set<String> permissions = getPermissions(userId);
        return permissions.contains(SYSTEM_ADMIN_PERMISSION);
    }

    /**
     * 判断请求的权限是否在当前 API Key 的 scope 范围内。
     * <p>
     * 规则：
     * - 非 API Key 认证（JWT 等） → 不限制（返回 true）
     * - API Key scope 为空 → 不限制（返回 true，向后兼容）
     * - API Key scope 非空 → permission 必须在 scope 中
     * <p>
     * 此方法确保 scope 过滤在权限检查的最底层执行，
     * 无论是 @PreAuthorize 触发还是 Service 层直接调用，都无法绕过。
     */
    private boolean isPermissionInCurrentScope(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiKeyAuthenticationToken apiKeyToken) {
            if (apiKeyToken.hasScopeRestriction()) {
                return apiKeyToken.getScope().contains(permission);
            }
        }
        return true;
    }

    /**
     * 获取用户所有权限（缓存优先）。
     * 空结果也会被缓存（使用占位符），防止无权限用户每次请求都穿透到 DB。
     * Redis 不可用时自动降级到数据库查询，保证系统可用性。
     */
    public Set<String> getPermissions(Long userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;

        // 1. 尝试从 Redis 读取缓存
        try {
            Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                if (cached.contains(EMPTY_PERMISSIONS_PLACEHOLDER)) {
                    return Set.of(); // 缓存命中：确认无权限
                }
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for global permissions (userId={}), falling back to DB: {}",
                    userId, e.getMessage());
            return loadPermissionsFromDb(userId);
        }

        // 2. 缓存未命中，从数据库加载
        Set<String> permissions = loadPermissionsFromDb(userId);

        // 3. 尝试写入缓存（best-effort，失败不影响返回结果）
        try {
            if (permissions.isEmpty()) {
                redisTemplate.opsForSet().add(cacheKey, EMPTY_PERMISSIONS_PLACEHOLDER);
            } else {
                redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
            }
            redisTemplate.expire(cacheKey, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for global permissions (userId={}): {}", userId, e.getMessage());
        }

        return permissions;
    }

    /**
     * 失效指定用户的所有权限缓存（全局 + 导航 + 所有项目级）
     * 使用 SCAN 迭代匹配项目级 key，避免 KEYS 命令阻塞 Redis。
     * Redis 不可用时仅记录警告，不阻断调用方（缓存会在 TTL 后自然过期）。
     */
    public void invalidateCache(Long userId) {
        try {
            // 1. 删除全局权限缓存
            String globalKey = CACHE_KEY_PREFIX + userId;
            redisTemplate.delete(globalKey);

            // 2. 删除导航权限缓存
            String navKey = NAV_CACHE_KEY_PREFIX + userId;
            redisTemplate.delete(navKey);

            // 3. 使用 SCAN 迭代删除该用户所有项目级权限缓存
            String projectPattern = PROJECT_CACHE_KEY_PREFIX + userId + ":*";
            Set<String> projectKeys = scanKeys(projectPattern);
            if (!projectKeys.isEmpty()) {
                redisTemplate.delete(projectKeys);
                log.debug("Permission cache invalidated for user {}: global + nav + {} project keys", userId, projectKeys.size());
            } else {
                log.debug("Permission cache invalidated for user {}: global + nav", userId);
            }
        } catch (Exception e) {
            log.warn("Redis cache invalidation failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 使用 SCAN 命令迭代匹配 Redis key（非阻塞，生产安全）
     * 每次迭代扫描 100 个 key，避免长时间阻塞 Redis。
     * Redis 不可用时返回空集合。
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("Redis SCAN failed for pattern '{}': {}", pattern, e.getMessage());
        }
        return keys;
    }

    /**
     * 失效指定项目的所有用户权限缓存。
     * <p>
     * 当项目 visibility 变更时调用——因为 NonMember/Anonymous 权限基于 visibility 动态授予，
     * 变更后必须清除所有用户在该项目上的缓存，强制下次请求重新计算。
     * <p>
     * 使用 SCAN 匹配 perm:user:project:*:{projectId} 模式，避免 KEYS 阻塞。
     * Redis 不可用时仅记录警告，缓存会在 TTL 后自然过期。
     *
     * @param projectId 项目 ID
     * @return 清除的缓存 key 数量
     */
    public int invalidateCacheForProject(Long projectId) {
        try {
            String pattern = PROJECT_CACHE_KEY_PREFIX + "*:" + projectId;
            Set<String> keys = scanKeys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.unlink(keys);
                log.debug("Permission cache invalidated for project {}: {} keys removed", projectId, keys.size());
            }
            return keys.size();
        } catch (Exception e) {
            log.warn("Redis cache invalidation failed for project {}: {}", projectId, e.getMessage());
            return 0;
        }
    }

    /**
     * 失效拥有指定角色的所有用户的权限缓存（直接分配 + 组继承）
     * <p>
     * 覆盖两条路径：
     * 1. user_role → 直接拥有该角色的用户
     * 2. user_group_role → user_group_member → 通过组继承该角色的用户
     * <p>
     * 合并去重后统一清除缓存，确保角色权限变更即时生效。
     */
    public void invalidateCacheForRole(Long roleId) {
        // 路径1: 直接分配该角色的用户
        List<Long> directUserIds = userRoleMapper.selectUserIdsByRoleId(roleId);

        // 路径2: 通过用户组继承该角色的用户
        List<Long> groupUserIds = userGroupRoleMapper.selectUserIdsByRoleIdViaGroup(roleId);

        // 合并去重
        Set<Long> allAffectedUserIds = new HashSet<>(directUserIds);
        allAffectedUserIds.addAll(groupUserIds);

        for (Long userId : allAffectedUserIds) {
            invalidateCache(userId);
        }
        log.debug("Permission cache invalidated for {} users of role {} (direct: {}, via groups: {})",
                allAffectedUserIds.size(), roleId, directUserIds.size(), groupUserIds.size());
    }

    /**
     * 从数据库加载用户权限（直接角色 + 组继承）
     */
    private Set<String> loadPermissionsFromDb(Long userId) {
        Set<String> permissions = new HashSet<>();

        // 1. 直接分配的全局角色权限
        List<String> directPerms = rolePermissionMapper.selectPermissionsByUserId(userId);
        permissions.addAll(directPerms);

        // 2. 通过用户组继承的全局权限
        List<String> groupPerms = userGroupRoleMapper.selectGlobalPermissionsByUserId(userId);
        permissions.addAll(groupPerms);

        return permissions;
    }

    /**
     * 获取用户在指定项目中的权限（缓存优先）。
     * 空结果也会被缓存（使用占位符），防止非成员用户对私有项目每次请求都穿透到 DB。
     * Redis 不可用时自动降级到数据库查询，保证系统可用性。
     *
     * 逻辑：
     * 1. 先查成员角色权限 → 有则返回
     * 2. 无成员关系时，查项目 visibility
     * 3. visibility = internal/public 且用户已登录 → 返回 NonMember 角色权限
     * 4. visibility = public 且未登录 → 返回 Anonymous 角色权限
     * 5. 对结果按项目启用模块过滤（只保留启用模块下的权限）
     */
    public Set<String> getProjectPermissions(Long userId, Long projectId) {
        String cacheKey = PROJECT_CACHE_KEY_PREFIX + userId + ":" + projectId;

        // 1. 尝试从 Redis 读取缓存
        try {
            Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                if (cached.contains(EMPTY_PERMISSIONS_PLACEHOLDER)) {
                    return Set.of(); // 缓存命中：确认无权限
                }
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for project permissions (userId={}, projectId={}), falling back to DB: {}",
                    userId, projectId, e.getMessage());
            return loadProjectPermissionsWithFallback(userId, projectId);
        }

        // 2. 缓存未命中，从数据库加载
        Set<String> permissions = loadProjectPermissionsWithFallback(userId, projectId);

        // 3. 尝试写入缓存（best-effort，失败不影响返回结果）
        try {
            if (permissions.isEmpty()) {
                redisTemplate.opsForSet().add(cacheKey, EMPTY_PERMISSIONS_PLACEHOLDER);
            } else {
                redisTemplate.opsForSet().add(cacheKey, permissions.toArray(new String[0]));
            }
            redisTemplate.expire(cacheKey, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for project permissions (userId={}, projectId={}): {}",
                    userId, projectId, e.getMessage());
        }

        return permissions;
    }

    /**
     * 从数据库加载项目权限，含 NonMember fallback 和模块过滤逻辑。
     * 提取为独立方法，供缓存命中失败和缓存未命中两个路径复用。
     */
    private Set<String> loadProjectPermissionsWithFallback(Long userId, Long projectId) {
        // 从数据库加载：project_member → role_permission
        Set<String> permissions = loadProjectPermissionsFromDb(userId, projectId);

        // 如果用户不是成员，尝试 NonMember fallback
        if (permissions.isEmpty() && userId != null) {
            permissions = loadNonMemberPermissions(projectId);
        }

        // 模块过滤：只保留项目已启用模块下的权限
        if (!permissions.isEmpty()) {
            permissions = projectModuleService.filterByEnabledModules(
                    projectId, permissions, permissionCategoryMap);
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
     * 从数据库加载用户在项目中的权限（直接成员角色 + 组继承 + 全局分配角色）
     */
    private Set<String> loadProjectPermissionsFromDb(Long userId, Long projectId) {
        Set<String> permissions = new HashSet<>();

        // 1. 直接项目成员角色权限
        List<String> directPerms = rolePermissionMapper.selectPermissionsByUserAndProject(userId, projectId);
        permissions.addAll(directPerms);

        // 2. 通过用户组继承的项目级权限
        List<String> groupPerms = userGroupRoleMapper.selectProjectPermissionsByUserAndProject(userId, projectId);
        permissions.addAll(groupPerms);

        // 3. 通过全局项目角色分配获得的权限（global_member 表）
        List<String> globalMemberPerms = globalMemberMapper.selectGlobalMemberPermissions(userId);
        permissions.addAll(globalMemberPerms);

        return permissions;
    }

    /**
     * 检查用户是否在任何项目中拥有指定权限。
     * 用于导航级别的权限判断（如：用户在任何项目中是否可以管理工作流）。
     * 检查范围包括：直接项目成员角色 + 组继承的项目角色。
     * <p>
     * 内置 API Key scope 过滤。
     */
    public boolean hasPermissionInAnyProject(Long userId, String permission) {
        if (userId == null) return false;

        // API Key scope 过滤
        if (!isPermissionInCurrentScope(permission)) {
            return false;
        }

        // system:admin 拥有所有权限
        if (isSystemAdmin(userId)) return true;
        // 1. 直接项目成员角色
        if (rolePermissionMapper.hasPermissionInAnyProject(userId, permission)) {
            return true;
        }
        // 2. 通过用户组继承的项目角色
        List<String> groupProjectPerms = userGroupRoleMapper.selectAllProjectPermissionsByUserId(userId);
        return groupProjectPerms.contains(permission);
    }

    /**
     * 获取用户拥有指定权限的项目 ID 列表。
     * 合并直接项目成员角色和组继承两条路径。
     * system_admin 返回 null（表示"所有项目"，由调用方处理）。
     *
     * @param userId     用户 ID
     * @param permission 权限代码（如 "time:view_others"）
     * @return 项目 ID 列表；system_admin 返回 null 表示不限制
     */
    public List<Long> getProjectIdsWithPermission(Long userId, String permission) {
        if (userId == null) return List.of();

        // system:admin 拥有所有权限，不限项目
        if (isSystemAdmin(userId)) return null;

        // 检查通过组继承的"全局作用域项目角色"（project_id IS NULL + role_type='project'）
        // 如果有，说明该用户在所有项目中都拥有该权限
        if (userGroupRoleMapper.hasGlobalScopePermissionViaGroups(userId, permission)) {
            return null;
        }

        Set<Long> projectIds = new HashSet<>();
        // 1. 直接项目成员角色
        projectIds.addAll(rolePermissionMapper.selectProjectIdsWithPermission(userId, permission));
        // 2. 通过组继承的精确绑定项目
        projectIds.addAll(userGroupRoleMapper.selectProjectIdsWithPermissionViaGroups(userId, permission));
        return new ArrayList<>(projectIds);
    }

    /**
     * 一次性获取用户在所有项目中的去重权限集合（直接成员角色 + 组继承）。
     * 用于导航权限聚合计算，替代多次 hasPermissionInAnyProject 串行调用。
     *
     * @param userId 用户 ID
     * @return 用户在所有项目中拥有的去重权限集合
     */
    public Set<String> getAllProjectPermissionsForUser(Long userId) {
        if (userId == null) return Set.of();
        Set<String> permissions = new HashSet<>();
        // 1. 直接项目成员角色的权限
        List<String> directPerms = rolePermissionMapper.selectAllProjectPermissionsByUserId(userId);
        permissions.addAll(directPerms);
        // 2. 通过用户组继承的项目角色权限
        List<String> groupPerms = userGroupRoleMapper.selectAllProjectPermissionsByUserId(userId);
        permissions.addAll(groupPerms);
        return permissions;
    }

    /**
     * 获取用户的导航权限集合（Redis 缓存优先）。
     * 结合全局权限 + 项目级权限聚合派生 nav:* 权限。
     * 空结果也会被缓存（使用占位符），防止穿透。
     * Redis 不可用时自动降级到数据库查询，保证系统可用性。
     * <p>
     * 缓存命中：0 DB 查询
     * 缓存未命中：最多 2 次 DB 查询（1 次全局权限 + 1 次聚合项目权限）
     *
     * @param userId 用户 ID
     * @return 包含全局权限和 nav:* 派生权限的完整集合
     */
    public Set<String> getNavigationPermissions(Long userId) {
        String navCacheKey = NAV_CACHE_KEY_PREFIX + userId;

        // 1. 尝试从 Redis 获取缓存
        try {
            Set<String> cached = redisTemplate.opsForSet().members(navCacheKey);
            if (cached != null && !cached.isEmpty()) {
                if (cached.contains(EMPTY_PERMISSIONS_PLACEHOLDER)) {
                    return Set.of(); // 缓存命中：确认无权限
                }
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for navigation permissions (userId={}), falling back to DB: {}",
                    userId, e.getMessage());
            return computeNavigationPermissions(userId);
        }

        // 2. 缓存未命中，计算导航权限
        Set<String> permissions = computeNavigationPermissions(userId);

        // 3. 尝试写入 Redis 缓存（best-effort）
        try {
            if (permissions.isEmpty()) {
                redisTemplate.opsForSet().add(navCacheKey, EMPTY_PERMISSIONS_PLACEHOLDER);
            } else {
                redisTemplate.opsForSet().add(navCacheKey, permissions.toArray(new String[0]));
            }
            redisTemplate.expire(navCacheKey, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for navigation permissions (userId={}): {}", userId, e.getMessage());
        }

        return permissions;
    }

    /**
     * 计算用户的导航权限集合（全局权限 + nav:* 派生权限）。
     * 提取为独立方法，供缓存命中失败和缓存未命中两个路径复用。
     */
    private Set<String> computeNavigationPermissions(Long userId) {
        Set<String> permissions = new HashSet<>(getPermissions(userId));

        if (!permissions.contains(SYSTEM_ADMIN_PERMISSION)) {
            // 一次性加载用户所有项目级权限（2 次 DB 查询代替 8-11 次）
            Set<String> allProjectPerms = getAllProjectPermissionsForUser(userId);

            if (allProjectPerms.contains("project:manage_workflow")) {
                permissions.add("nav:workflow");
            }
            if (allProjectPerms.contains("issue:create")) {
                permissions.add("nav:create_issue");
            }
            if (allProjectPerms.contains("report:view")) {
                permissions.add("nav:report");
            }
            if (allProjectPerms.contains("report:create")) {
                permissions.add("nav:report_create");
            }
            if (allProjectPerms.contains("sprint:create")) {
                permissions.add("nav:sprint_manage");
            }
            if (allProjectPerms.contains("sprint:view")) {
                permissions.add("nav:sprint_view");
            }
            if (allProjectPerms.contains("issue:delete")) {
                permissions.add("nav:trash");
            }
            if (allProjectPerms.contains("time:log") || allProjectPerms.contains("time:view_others")) {
                permissions.add("nav:timesheet");
            }
            if (allProjectPerms.contains("issue:edit")
                    || allProjectPerms.contains("issue:delete")
                    || allProjectPerms.contains("issue:assign")
                    || allProjectPerms.contains("issue:change_status")) {
                permissions.add("nav:batch_ops");
            }
        }

        return permissions;
    }

    /**
     * 检查用户对特定 Issue 的权限（含资源级规则）。
     *
     * <p>权限检查优先级：
     * <ol>
     *   <li>项目级角色权限：直接通过 hasPermission 判断（含 system:admin 检查）</li>
     *   <li>固有权限（Inherent Permissions）：基于资源所有权的无条件自动授权
     *       <ul>
     *         <li>reporter 对自己创建的工单天然继承 view/edit/comment 权限（无需额外权限码）</li>
     *         <li>assignee 对分配给自己的工单天然继承 view 权限</li>
     *       </ul>
     *   </li>
     *   <li>资源级规则（可通过角色配置开启/关闭）：
     *       <ul>
     *         <li>assignee 需要 issue:edit_assigned 权限才能编辑/变更状态</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>固有权限参考 YouTrack Inherent Permissions 规则：
     * Issue reporter 永远有权查看、编辑自己创建的工单的公共字段，即使角色中没有显式授予
     * Read Issue 或 Update Issue 权限。
     *
     * @param userId     当前操作用户
     * @param issue      目标 Issue 对象
     * @param permission 要检查的权限码
     * @return true 如果用户有权限
     */
    public boolean hasIssuePermission(Long userId, Issue issue, String permission) {
        if (userId == null || issue == null) return false;

        // API Key scope 过滤：即使是固有权限，也必须在 scope 范围内
        if (!isPermissionInCurrentScope(permission)) {
            return false;
        }

        // 1. 项目级权限直接满足（含 system:admin 检查，hasPermission 内部已含 scope 检查，此处不会重复）
        if (hasPermission(userId, issue.getProjectId(), permission)) {
            return true;
        }

        // 2. 固有权限（Inherent Permissions）：基于资源所有权的无条件自动授权
        //    参考 YouTrack："Issue reporters always have permission to view public fields,
        //    update public fields, and add links to the issues that they created...
        //    even when they don't have Read Issue, Update Issue, and Link Issues permissions."
        if (Objects.equals(userId, issue.getReporterId())) {
            // Reporter 对自己创建的工单天然继承 view/edit/comment 权限
            if (REPORTER_INHERENT_PERMISSIONS.contains(permission)) {
                return true;
            }
        }

        // Assignee 对分配给自己的工单天然继承 view 权限
        if (Objects.equals(userId, issue.getAssigneeId())) {
            if (ASSIGNEE_INHERENT_PERMISSIONS.contains(permission)) {
                return true;
            }
        }

        // 3. 资源级规则：负责人（assignee）编辑——需要 issue:edit_assigned 权限
        if ("issue:edit".equals(permission) && Objects.equals(userId, issue.getAssigneeId())) {
            return hasPermission(userId, issue.getProjectId(), "issue:edit_assigned");
        }

        // 4. 资源级规则：负责人变更状态——也需要 issue:edit_assigned 权限
        if ("issue:change_status".equals(permission) && Objects.equals(userId, issue.getAssigneeId())) {
            return hasPermission(userId, issue.getProjectId(), "issue:edit_assigned");
        }

        return false;
    }
}

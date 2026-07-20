package com.trackflow.project.service;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.event.ProjectVisibilityChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Set;

/**
 * 项目缓存失效事件监听器。
 * <p>
 * 监听 {@link ProjectVisibilityChangedEvent}，在事务提交后：
 * 1. 清除所有用户的 accessible_projects 缓存（项目列表可见性）
 * 2. 清除该项目的所有 perm:user:project:*:{projectId} 权限缓存（权限变更即时生效）
 * <p>
 * 设计要点：
 * - 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保 Redis SCAN 不阻塞 DB 事务
 * - 使用 SCAN 避免 KEYS 命令阻塞 Redis 主线程
 * - 使用 unlink 异步释放内存（替代 delete 同步释放）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCacheEventListener {

    private final StringRedisTemplate redisTemplate;
    private final PermissionService permissionService;

    private static final String ACCESSIBLE_PROJECTS_CACHE_PREFIX = "accessible_projects:";

    /**
     * 项目可见性变更后：
     * 1. 清除所有用户的 accessible_projects 缓存
     * 2. 清除该项目的所有用户权限缓存（NonMember 权限基于 visibility 动态授予）
     * <p>
     * visibility 变更影响范围不可预测（所有非成员用户都可能受影响），
     * 因此需要全量清除。使用 SCAN + UNLINK 模式确保不阻塞 Redis。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVisibilityChanged(ProjectVisibilityChangedEvent event) {
        log.info("Project visibility changed (projectId={}, {} → {}), invalidating caches",
                event.projectId(), event.oldVisibility(), event.newVisibility());

        // 1. 清除 accessible_projects 缓存
        invalidateAccessibleProjectsCaches();

        // 2. 清除该项目的所有权限缓存（关键修复：确保 NonMember 权限变更即时生效）
        int permKeysRemoved = permissionService.invalidateCacheForProject(event.projectId());

        log.info("Visibility change cache invalidation complete (projectId={}): permission keys removed={}",
                event.projectId(), permKeysRemoved);
    }

    /**
     * 清除所有用户的 accessible_projects 缓存
     */
    private void invalidateAccessibleProjectsCaches() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(ACCESSIBLE_PROJECTS_CACHE_PREFIX + "*")
                .count(200)
                .build();

        try (var cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("Failed to SCAN accessible_projects cache keys: {}", e.getMessage());
            return;
        }

        if (!keys.isEmpty()) {
            try {
                redisTemplate.unlink(keys);
                log.debug("Invalidated {} accessible_projects cache keys", keys.size());
            } catch (Exception e) {
                log.warn("Failed to UNLINK {} accessible_projects cache keys: {}", keys.size(), e.getMessage());
            }
        }
    }
}

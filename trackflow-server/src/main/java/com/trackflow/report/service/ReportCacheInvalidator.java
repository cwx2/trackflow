package com.trackflow.report.service;

import com.trackflow.common.event.ReportCacheInvalidationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashSet;
import java.util.Set;

/**
 * 报表缓存失效监听器。
 * <p>
 * 监听 {@link ReportCacheInvalidationEvent}，在事务提交后清除受影响项目的 Dashboard 缓存。
 * 使用 Redis SCAN + UNLINK 按模式匹配删除，避免阻塞 Redis 主线程。
 * <p>
 * 缓存 key 格式：
 * - 单项目：{@code report:dashboard:{projectId}:{sprintId|none}:{startDate}:{endDate}}
 * - 全部项目：{@code report:dashboard:all_{projectIdsHash}:{sprintId|none}:{startDate}:{endDate}}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCacheInvalidator {

    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "report:dashboard:";

    /**
     * 事务提交后清除受影响项目的 Dashboard 缓存。
     * <p>
     * 策略：
     * - 精准删除指定 projectId 对应的缓存 key
     * - 同时删除 "all" 项目的缓存（因为全局 Dashboard 也包含该项目数据）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCacheInvalidation(ReportCacheInvalidationEvent event) {
        Set<Long> projectIds = event.projectIds();
        String reason = event.reason();

        if (projectIds == null || projectIds.isEmpty()) {
            log.debug("Report cache invalidation skipped: no projectIds (reason={})", reason);
            return;
        }

        int deletedCount = 0;

        // 1. 删除各 projectId 对应的缓存
        for (Long projectId : projectIds) {
            String pattern = CACHE_PREFIX + projectId + ":*";
            deletedCount += deleteByPattern(pattern);
        }

        // 2. 删除"全部项目"模式的缓存（key 格式为 report:dashboard:all_{hash}:...）
        String allPattern = CACHE_PREFIX + "all_*";
        deletedCount += deleteByPattern(allPattern);

        if (deletedCount > 0) {
            log.debug("Report cache invalidated: {} keys deleted (projects={}, reason={})",
                    deletedCount, projectIds, reason);
        }
    }

    /**
     * 使用 SCAN + UNLINK 按模式删除 Redis key。
     * SCAN 避免阻塞，UNLINK 异步释放内存。
     *
     * @return 删除的 key 数量
     */
    private int deleteByPattern(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            try (var cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
        } catch (Exception e) {
            log.warn("Report cache SCAN failed (pattern={}): {}", pattern, e.getMessage());
            return 0;
        }

        if (!keys.isEmpty()) {
            try {
                redisTemplate.unlink(keys);
            } catch (Exception e) {
                log.warn("Report cache UNLINK failed ({} keys): {}", keys.size(), e.getMessage());
                return 0;
            }
        }
        return keys.size();
    }
}

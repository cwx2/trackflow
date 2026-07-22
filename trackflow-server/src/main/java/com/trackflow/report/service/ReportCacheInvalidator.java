package com.trackflow.report.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.report.entity.ReportDefinition;
import com.trackflow.report.mapper.ReportDefinitionMapper;
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
 * 监听 {@link ReportCacheInvalidationEvent}，在事务提交后清除受影响项目的缓存：
 * 1. Redis Dashboard 缓存（report:dashboard:* keys）
 * 2. 数据库持久化的报表结果缓存（report_definition.cached_result）
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
    private final ReportDefinitionMapper reportDefinitionMapper;

    private static final String CACHE_PREFIX = "report:dashboard:";

    /**
     * 事务提交后清除受影响项目的 Dashboard 缓存和报表持久化缓存。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCacheInvalidation(ReportCacheInvalidationEvent event) {
        Set<Long> projectIds = event.projectIds();
        String reason = event.reason();

        if (projectIds == null || projectIds.isEmpty()) {
            log.debug("Report cache invalidation skipped: no projectIds (reason={})", reason);
            return;
        }

        // 1. 清除 Redis Dashboard 缓存
        int redisDeletedCount = invalidateRedisDashboardCache(projectIds);

        // 2. 清除数据库持久化的报表结果缓存
        int dbInvalidatedCount = invalidatePersistedReportCache(projectIds);

        if (redisDeletedCount > 0 || dbInvalidatedCount > 0) {
            log.debug("Report cache invalidated: redis={} keys, db={} reports (projects={}, reason={})",
                    redisDeletedCount, dbInvalidatedCount, projectIds, reason);
        }
    }

    /**
     * 清除受影响项目的 Redis Dashboard 缓存
     */
    private int invalidateRedisDashboardCache(Set<Long> projectIds) {
        int deletedCount = 0;

        // 删除各 projectId 对应的缓存
        for (Long projectId : projectIds) {
            String pattern = CACHE_PREFIX + projectId + ":*";
            deletedCount += deleteByPattern(pattern);
        }

        // 删除"全部项目"模式的缓存
        String allPattern = CACHE_PREFIX + "all_*";
        deletedCount += deleteByPattern(allPattern);

        return deletedCount;
    }

    /**
     * 清除受影响项目的数据库持久化报表缓存。
     * <p>
     * 将 report_definition.cached_result 和 last_calculated_at 设为 null，
     * 下次查看时会触发重新计算。
     */
    private int invalidatePersistedReportCache(Set<Long> projectIds) {
        // 清除指定项目的报表缓存
        LambdaUpdateWrapper<ReportDefinition> projectWrapper = new LambdaUpdateWrapper<>();
        projectWrapper.in(ReportDefinition::getProjectId, projectIds)
                .isNotNull(ReportDefinition::getCachedResult)
                .set(ReportDefinition::getCachedResult, null)
                .set(ReportDefinition::getLastCalculatedAt, null);
        int count = reportDefinitionMapper.update(null, projectWrapper);

        // 清除全局报表（projectId=null）的缓存，因为全局报表包含所有项目的数据
        LambdaUpdateWrapper<ReportDefinition> globalWrapper = new LambdaUpdateWrapper<>();
        globalWrapper.isNull(ReportDefinition::getProjectId)
                .isNotNull(ReportDefinition::getCachedResult)
                .set(ReportDefinition::getCachedResult, null)
                .set(ReportDefinition::getLastCalculatedAt, null);
        count += reportDefinitionMapper.update(null, globalWrapper);

        return count;
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

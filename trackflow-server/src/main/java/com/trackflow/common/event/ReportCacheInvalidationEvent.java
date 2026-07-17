package com.trackflow.common.event;

import java.util.Collection;
import java.util.Set;

/**
 * 报表缓存失效事件。
 * <p>
 * 当工单或 Sprint 发生写操作（创建/更新/删除/状态变更）时发布，
 * 由 {@link com.trackflow.report.service.ReportCacheInvalidator} 监听并清除相关的 Dashboard 缓存。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保事务成功后才失效缓存，
 * 避免事务回滚后缓存被错误清除导致不必要的 DB 重查。
 *
 * @param projectIds 受影响的项目 ID 集合（用于精准清除对应项目的缓存）
 * @param reason     触发原因（仅用于日志，如 "issue_created", "sprint_completed"）
 */
public record ReportCacheInvalidationEvent(Set<Long> projectIds, String reason) {

    /**
     * 单项目缓存失效
     */
    public static ReportCacheInvalidationEvent of(Long projectId, String reason) {
        return new ReportCacheInvalidationEvent(Set.of(projectId), reason);
    }

    /**
     * 多项目缓存失效（如批量操作涉及多个项目）
     */
    public static ReportCacheInvalidationEvent of(Collection<Long> projectIds, String reason) {
        return new ReportCacheInvalidationEvent(Set.copyOf(projectIds), reason);
    }
}

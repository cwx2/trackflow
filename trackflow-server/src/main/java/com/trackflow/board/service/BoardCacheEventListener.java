package com.trackflow.board.service;

import com.trackflow.common.event.ReportCacheInvalidationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 看板缓存失效监听器。
 * <p>
 * 监听 {@link ReportCacheInvalidationEvent}，在事务提交后清除受影响项目的看板列统计缓存。
 * <p>
 * 覆盖场景：工单创建/更新/删除/状态变更/移动项目/批量操作等所有影响列统计的写操作。
 * 这些场景已在 IssueService 中发布 ReportCacheInvalidationEvent，本监听器复用同一事件，
 * 避免在 IssueService 中直接注入 BoardColumnService（规避循环依赖风险）。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardCacheEventListener {

    private final BoardColumnService boardColumnService;

    /**
     * 事务提交后失效受影响项目的看板列缓存。
     * <p>
     * 使用 AFTER_COMMIT 确保事务成功后才失效缓存，
     * 避免事务回滚后缓存被错误清除导致不必要的 DB 重查。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueMutation(ReportCacheInvalidationEvent event) {
        if (event.projectIds() == null || event.projectIds().isEmpty()) {
            return;
        }

        for (Long projectId : event.projectIds()) {
            boardColumnService.invalidateCache(projectId);
        }

        log.debug("Board columns cache invalidated for projects={} (reason={})",
                event.projectIds(), event.reason());
    }
}

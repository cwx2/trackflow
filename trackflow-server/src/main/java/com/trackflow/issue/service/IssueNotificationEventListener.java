package com.trackflow.issue.service;

import com.trackflow.common.event.IssueNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Issue 通知事件监听器。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保通知仅在事务成功提交后触发。
 * 事务回滚时事件不会被消费，保证不会发出引用不存在资源的通知。
 * <p>
 * 实际通知发送委托给 IssueNotificationHelper（保留 @Async 异步执行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueNotificationEventListener {

    private final IssueNotificationHelper notificationHelper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreated(IssueNotificationEvent.Created event) {
        notificationHelper.notifyCreated(event.issue(), event.creatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAssigned(IssueNotificationEvent.Assigned event) {
        notificationHelper.notifyAssigned(event.issue(), event.assigneeId(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStatusChanged(IssueNotificationEvent.StatusChanged event) {
        notificationHelper.notifyStatusChanged(event.issue(), event.oldStatusId(), event.newStatusId(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommented(IssueNotificationEvent.Commented event) {
        notificationHelper.notifyCommented(event.issue(), event.commenterId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMentioned(IssueNotificationEvent.Mentioned event) {
        notificationHelper.notifyMentioned(event.issue(), event.commentContent(), event.commenterId());
    }
}

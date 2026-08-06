package com.trackflow.issue.service;

import com.trackflow.common.context.NotificationContext;
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
 * 当 {@link NotificationContext#isSilent()} 为 true 时（Apply without notice 模式），
 * 所有通知将被静默跳过。
 * <p>
 * 实际通知发送委托给 IssueNotificationHelper（保留 @Async 异步执行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssueNotificationEventListener {

    private final IssueNotificationHelper notificationHelper;
    private final IssueWatcherService watcherService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreated(IssueNotificationEvent.Created event) {
        // 自动关注：创建工单后
        watcherService.autoWatchOnCreate(event.issue().getId(), event.creatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyCreated(event.issue(), event.creatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAssigned(IssueNotificationEvent.Assigned event) {
        // 自动关注：被分配为负责人后
        if (event.assigneeId() != null) {
            watcherService.autoWatchOnAssign(event.issue().getId(), event.assigneeId());
        }
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyAssigned(event.issue(), event.assigneeId(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStatusChanged(IssueNotificationEvent.StatusChanged event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyStatusChanged(event.issue(), event.oldStatusId(), event.newStatusId(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommented(IssueNotificationEvent.Commented event) {
        // 自动关注：评论工单后
        watcherService.autoWatchOnComment(event.issue().getId(), event.commenterId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyCommented(event.issue(), event.commenterId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMentioned(IssueNotificationEvent.Mentioned event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyMentioned(event.issue(), event.commentContent(), event.commenterId(), event.commentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMoved(IssueNotificationEvent.Moved event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyMoved(event.issue(), event.sourceProjectId(), event.targetProjectId(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCancelled(IssueNotificationEvent.Cancelled event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyCancelled(event.issue(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFieldUpdated(IssueNotificationEvent.FieldUpdated event) {
        // 自动关注：修改工单后
        watcherService.autoWatchOnUpdate(event.issue().getId(), event.operatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyFieldUpdated(event.issue(), event.fieldName(), event.oldValue(), event.newValue(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMultiFieldUpdated(IssueNotificationEvent.MultiFieldUpdated event) {
        // 自动关注：修改工单后
        watcherService.autoWatchOnUpdate(event.issue().getId(), event.operatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyMultiFieldUpdated(event.issue(), event.changes(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAttachmentAdded(IssueNotificationEvent.AttachmentAdded event) {
        // 自动关注：上传附件后
        watcherService.autoWatchOnUpdate(event.issue().getId(), event.operatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyAttachmentAdded(event.issue(), event.fileName(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLinkChanged(IssueNotificationEvent.LinkChanged event) {
        // 自动关注：修改关联后
        watcherService.autoWatchOnUpdate(event.issue().getId(), event.operatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyLinkChanged(event.issue(), event.targetIssueKey(), event.linkType(), event.added(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTimeLogged(IssueNotificationEvent.TimeLogged event) {
        // 自动关注：记录工时后
        watcherService.autoWatchOnUpdate(event.issue().getId(), event.operatorId());
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyTimeLogged(event.issue(), event.durationMinutes(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRestored(IssueNotificationEvent.Restored event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyRestored(event.issue(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVoted(IssueNotificationEvent.Voted event) {
        if (NotificationContext.isSilent()) return;
        notificationHelper.notifyVoted(event.issue(), event.voterId(), event.voteCount());
    }
}

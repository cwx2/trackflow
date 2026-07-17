package com.trackflow.project.service;

import com.trackflow.common.event.ProjectNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Project 通知事件监听器。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保通知仅在事务成功提交后触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectNotificationEventListener {

    private final ProjectNotificationHelper notificationHelper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberAdded(ProjectNotificationEvent.MemberAdded event) {
        notificationHelper.notifyMemberAdded(event.userId(), event.operatorId(),
                event.projectId(), event.projectName(), event.roleNames());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoleChanged(ProjectNotificationEvent.RoleChanged event) {
        notificationHelper.notifyRoleChanged(event.userId(), event.operatorId(),
                event.projectId(), event.projectName(), event.newRoleNames());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberRemoved(ProjectNotificationEvent.MemberRemoved event) {
        notificationHelper.notifyMemberRemoved(event.userId(), event.operatorId(),
                event.projectId(), event.projectName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNewLead(ProjectNotificationEvent.NewLead event) {
        notificationHelper.notifyNewLead(event.newLeadId(), event.operatorId(),
                event.projectId(), event.projectName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOldLead(ProjectNotificationEvent.OldLead event) {
        notificationHelper.notifyOldLead(event.oldLeadId(), event.operatorId(),
                event.projectId(), event.projectName(), event.newLeadName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLifecycleEvent(ProjectNotificationEvent.LifecycleEvent event) {
        notificationHelper.notifyLifecycleEvent(event.projectId(), event.operatorId(),
                event.title(), event.content(), event.type());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProjectDeleted(ProjectNotificationEvent.ProjectDeleted event) {
        notificationHelper.notifyProjectDeleted(event.projectId(), event.memberUserIds(),
                event.operatorId(), event.projectName(), event.projectKey());
    }
}

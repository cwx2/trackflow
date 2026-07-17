package com.trackflow.sprint.service;

import com.trackflow.common.event.SprintNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 通知事件监听器。
 * <p>
 * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保通知仅在事务成功提交后触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SprintNotificationEventListener {

    private final SprintNotificationHelper notificationHelper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleActivated(SprintNotificationEvent.Activated event) {
        notificationHelper.notifySprintActivated(event.sprint(), event.operatorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCompleted(SprintNotificationEvent.Completed event) {
        notificationHelper.notifySprintCompleted(event.sprint(), event.completedIssues(), event.operatorId());
    }
}

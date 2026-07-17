package com.trackflow.common.event;

import com.trackflow.sprint.entity.Sprint;

/**
 * Sprint 模块通知事件（sealed 接口）。
 */
public sealed interface SprintNotificationEvent extends NotificationEvent {

    /**
     * Sprint 激活通知事件
     */
    record Activated(Sprint sprint, Long operatorId) implements SprintNotificationEvent {}

    /**
     * Sprint 完成通知事件
     */
    record Completed(Sprint sprint, int completedIssues, Long operatorId) implements SprintNotificationEvent {}
}

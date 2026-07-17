package com.trackflow.common.event;

import com.trackflow.integration.entity.NotificationType;

import java.util.List;

/**
 * Project 模块通知事件（sealed 接口）。
 */
public sealed interface ProjectNotificationEvent extends NotificationEvent {

    /**
     * 成员被添加到项目
     */
    record MemberAdded(Long userId, Long operatorId, Long projectId,
                       String projectName, String roleNames) implements ProjectNotificationEvent {}

    /**
     * 成员角色变更
     */
    record RoleChanged(Long userId, Long operatorId, Long projectId,
                       String projectName, String newRoleNames) implements ProjectNotificationEvent {}

    /**
     * 成员被移出项目
     */
    record MemberRemoved(Long userId, Long operatorId, Long projectId,
                         String projectName) implements ProjectNotificationEvent {}

    /**
     * 新负责人通知
     */
    record NewLead(Long newLeadId, Long operatorId, Long projectId,
                   String projectName) implements ProjectNotificationEvent {}

    /**
     * 旧负责人通知
     */
    record OldLead(Long oldLeadId, Long operatorId, Long projectId,
                   String projectName, String newLeadName) implements ProjectNotificationEvent {}

    /**
     * 项目生命周期事件（归档/恢复）
     */
    record LifecycleEvent(Long projectId, Long operatorId,
                          String title, String content, NotificationType type) implements ProjectNotificationEvent {}

    /**
     * 项目删除通知（强制，不受偏好控制）
     */
    record ProjectDeleted(Long projectId, List<Long> memberUserIds, Long operatorId,
                          String projectName, String projectKey) implements ProjectNotificationEvent {}
}

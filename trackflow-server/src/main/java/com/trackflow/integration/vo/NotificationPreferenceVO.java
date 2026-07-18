package com.trackflow.integration.vo;

import lombok.Data;

@Data
public class NotificationPreferenceVO {
    private String id;
    private String userId;
    /** NULL 表示全局偏好，非 NULL 表示项目级偏好 */
    private String projectId;

    // Issue 事件订阅开关
    private Boolean onIssueAssigned;
    private Boolean onIssueStatusChanged;
    private Boolean onIssueCommented;
    private Boolean onMentioned;
    private Boolean onIssueResolved;

    // Sprint 事件订阅开关
    private Boolean onSprintStarted;
    private Boolean onSprintCompleted;

    // 项目事件订阅开关
    private Boolean onProjectMemberChanged;
    private Boolean onProjectLifecycle;

    // 日期提醒相关
    private Boolean onDueDate;
    private Boolean onOverdue;
    private Integer dueDateAdvanceDays;

    // 自己的操作是否通知自己
    private Boolean notifyOwnChanges;

    // 邮件通知渠道
    private Boolean emailEnabled;

    // 静音时段
    private String quietHoursStart;
    private String quietHoursEnd;
}

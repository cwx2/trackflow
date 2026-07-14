package com.trackflow.integration.dto;

import lombok.Data;

@Data
public class UpdateNotificationPreferenceDTO {

    // 事件订阅开关
    private Boolean onIssueAssigned;
    private Boolean onIssueStatusChanged;
    private Boolean onIssueCommented;
    private Boolean onMentioned;
    private Boolean onIssueResolved;
    private Boolean onSprintStarted;
    private Boolean onSprintCompleted;

    // 邮件通知渠道
    private Boolean emailEnabled;

    // 静音时段（HH:mm 格式）
    private String quietHoursStart;
    private String quietHoursEnd;
}

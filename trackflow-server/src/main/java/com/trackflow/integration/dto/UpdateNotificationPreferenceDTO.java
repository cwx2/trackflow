package com.trackflow.integration.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNotificationPreferenceDTO {

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

    // 自己的操作是否通知自己
    private Boolean notifyOwnChanges;

    // 邮件通知渠道
    private Boolean emailEnabled;

    // 静音时段（HH:mm 格式）
    @Size(max = 5)
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "时间格式必须为HH:mm")
    private String quietHoursStart;

    @Size(max = 5)
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "时间格式必须为HH:mm")
    private String quietHoursEnd;
}

package com.trackflow.integration.dto;

import lombok.Data;

/**
 * 更新通知管理设置 DTO
 */
@Data
public class UpdateNotificationSettingsDTO {

    // === 通知渠道 ===
    private Boolean inAppEnabled;
    private Boolean emailEnabled;

    // === 保留策略 ===
    private Integer retentionDays;

    // === 新用户默认偏好 ===
    private Boolean defaultOnIssueAssigned;
    private Boolean defaultOnIssueStatusChanged;
    private Boolean defaultOnIssueCommented;
    private Boolean defaultOnMentioned;
    private Boolean defaultOnIssueResolved;
    private Boolean defaultOnSprintStarted;
    private Boolean defaultOnSprintCompleted;
    private Boolean defaultOnProjectMemberChanged;
    private Boolean defaultOnProjectLifecycle;
    private Boolean defaultNotifyOwnChanges;
    private Boolean defaultEmailEnabled;
}

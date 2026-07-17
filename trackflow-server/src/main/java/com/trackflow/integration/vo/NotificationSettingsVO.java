package com.trackflow.integration.vo;

import lombok.Data;

/**
 * 通知管理设置 VO（管理员视图）
 */
@Data
public class NotificationSettingsVO {

    // === 通知渠道 ===
    /** 站内通知全局开关 */
    private Boolean inAppEnabled;
    /** 邮件通知全局开关 */
    private Boolean emailEnabled;

    // === 保留策略 ===
    /** 已读通知保留天数（0=永久保留） */
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
    private Boolean defaultEmailEnabled;
}

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
    private Boolean onIssueUpdated;

    // Sprint 事件订阅开关
    private Boolean onSprintStarted;
    private Boolean onSprintCompleted;

    // 项目事件订阅开关
    private Boolean onProjectMemberChanged;
    private Boolean onProjectLifecycle;

    // 日期提醒相关
    private Boolean onDueDate;
    private Boolean onOverdue;
    @jakarta.validation.constraints.Min(value = 1, message = "提前提醒天数最少为1天")
    @jakarta.validation.constraints.Max(value = 14, message = "提前提醒天数最多为14天")
    private Integer dueDateAdvanceDays;

    // 自己的操作是否通知自己
    private Boolean notifyOwnChanges;

    // 邮件通知渠道（总开关）
    private Boolean emailEnabled;

    // Per-event 邮件渠道控制
    private Boolean emailOnIssueAssigned;
    private Boolean emailOnIssueStatusChanged;
    private Boolean emailOnIssueCommented;
    private Boolean emailOnMentioned;
    private Boolean emailOnIssueResolved;
    private Boolean emailOnIssueUpdated;
    private Boolean emailOnSprintStarted;
    private Boolean emailOnSprintCompleted;
    private Boolean emailOnProjectMemberChanged;
    private Boolean emailOnProjectLifecycle;
    private Boolean emailOnDueDate;
    private Boolean emailOnOverdue;
    private Boolean emailOnWatchedUpdated;

    // 静音时段（HH:mm 格式）
    @Size(max = 5)
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "时间格式必须为HH:mm")
    private String quietHoursStart;

    @Size(max = 5)
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "时间格式必须为HH:mm")
    private String quietHoursEnd;

    // 关注（Watched）通知开关
    private Boolean onWatchedUpdated;

    // Vote + Spent Time 事件开关
    private Boolean onIssueVoted;
    private Boolean emailOnIssueVoted;
    private Boolean onIssueSpentTime;
    private Boolean emailOnIssueSpentTime;

    // 自动关注行为配置
    private Boolean autoWatchOnCreate;
    private Boolean autoWatchOnComment;
    private Boolean autoWatchOnUpdate;
    private Boolean autoWatchOnAssign;
}

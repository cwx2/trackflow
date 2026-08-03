package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "notification_preference", autoResultMap = true)
public class NotificationPreference implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    /** NULL 表示全局偏好，非 NULL 表示项目级偏好覆盖 */
    private Long projectId;

    // Issue 事件订阅开关
    private Boolean onIssueAssigned;
    private Boolean onIssueStatusChanged;
    private Boolean onIssueCommented;
    private Boolean onMentioned;
    private Boolean onIssueResolved;
    /** 通用字段变更（priority, dueDate, description, sprint, parent, tags） */
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
    private Integer dueDateAdvanceDays;
    /**
     * 逾期提醒间隔天数数组。仅在这些逾期天数时发送通知。
     * 默认 [1, 3, 7, 14]，即逾期第1天、第3天、第7天、第14天提醒。
     * 参考 OpenProject DATE_ALERT_OVERDUE_DURATIONS。
     */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = com.trackflow.common.handler.IntegerArrayTypeHandler.class)
    private Integer[] overdueReminderDays;

    // 自己的操作是否通知自己（默认 false，与 YouTrack "Changes applied by me" 一致）
    private Boolean notifyOwnChanges;

    // 邮件通知渠道（总开关）
    private Boolean emailEnabled;

    // Per-event 邮件渠道控制（仅在 emailEnabled=true 时生效）
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

    // 静音时段
    private String quietHoursStart;
    private String quietHoursEnd;

    // 关注（Watched）通知开关
    /** 我关注的工单有更新时通知我 */
    private Boolean onWatchedUpdated;

    // Vote + Spent Time 事件开关（对标 YouTrack 订阅事件列表）
    /** 工单被投票时通知我（站内） */
    private Boolean onIssueVoted;
    /** 工单被投票时通知我（邮件） */
    private Boolean emailOnIssueVoted;
    /** 工单有工时记录变更时通知我（站内） */
    private Boolean onIssueSpentTime;
    /** 工单有工时记录变更时通知我（邮件） */
    private Boolean emailOnIssueSpentTime;

    // 自动关注行为配置
    /** 创建工单时自动关注 */
    private Boolean autoWatchOnCreate;
    /** 评论工单时自动关注 */
    private Boolean autoWatchOnComment;
    /** 修改工单时自动关注 */
    private Boolean autoWatchOnUpdate;
    /** 被分配为负责人时自动关注 */
    private Boolean autoWatchOnAssign;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

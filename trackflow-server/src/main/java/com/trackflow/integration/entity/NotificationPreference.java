package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("notification_preference")
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

    // 自己的操作是否通知自己（默认 false，与 YouTrack "Changes applied by me" 一致）
    private Boolean notifyOwnChanges;

    // 邮件通知渠道
    private Boolean emailEnabled;

    // 静音时段
    private String quietHoursStart;
    private String quietHoursEnd;

    // 关注（Watched）通知开关
    /** 我关注的工单有更新时通知我 */
    private Boolean onWatchedUpdated;

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

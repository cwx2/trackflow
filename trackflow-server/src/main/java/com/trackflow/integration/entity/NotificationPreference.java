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

    // Sprint 事件订阅开关
    private Boolean onSprintStarted;
    private Boolean onSprintCompleted;

    // 项目事件订阅开关
    private Boolean onProjectMemberChanged;
    private Boolean onProjectLifecycle;

    // 邮件通知渠道
    private Boolean emailEnabled;

    // 静音时段
    private String quietHoursStart;
    private String quietHoursEnd;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

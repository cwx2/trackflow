package com.trackflow.integration.entity;

import java.util.function.Function;

/**
 * 通知事件类型枚举——绑定偏好检查逻辑，确保编译期类型安全。
 * <p>
 * 每个枚举值对应 {@link NotificationPreference} 中的一个布尔字段，
 * 通过函数式映射实现偏好提取，避免字符串 switch-case 的遗漏风险。
 * <p>
 * 新增事件类型时：
 * 1. 在此枚举中添加常量并绑定偏好提取器
 * 2. 确保 {@link NotificationPreference} 有对应字段
 * 3. Helper 中直接使用新枚举值即可——编译器保证不遗漏
 */
public enum NotificationEventType {

    // ===== Issue 相关 =====
    ISSUE_ASSIGNED(NotificationPreference::getOnIssueAssigned),
    ISSUE_STATUS_CHANGED(NotificationPreference::getOnIssueStatusChanged),
    ISSUE_COMMENTED(NotificationPreference::getOnIssueCommented),
    ISSUE_RESOLVED(NotificationPreference::getOnIssueResolved),
    MENTIONED(NotificationPreference::getOnMentioned),

    // ===== Sprint 相关 =====
    SPRINT_STARTED(NotificationPreference::getOnSprintStarted),
    SPRINT_COMPLETED(NotificationPreference::getOnSprintCompleted),

    // ===== Project 相关 =====
    PROJECT_MEMBER_CHANGED(NotificationPreference::getOnProjectMemberChanged),
    PROJECT_LIFECYCLE(NotificationPreference::getOnProjectLifecycle),

    // ===== Date Alert 相关 =====
    DUE_DATE_APPROACHING(NotificationPreference::getOnDueDate),
    OVERDUE(NotificationPreference::getOnOverdue);

    private final Function<NotificationPreference, Boolean> extractor;

    NotificationEventType(Function<NotificationPreference, Boolean> extractor) {
        this.extractor = extractor;
    }

    /**
     * 检查给定偏好中此事件类型是否启用。
     * null 视为未设置，按默认发送处理（返回 true）。
     */
    public boolean isEnabled(NotificationPreference pref) {
        Boolean value = extractor.apply(pref);
        return !Boolean.FALSE.equals(value);
    }
}

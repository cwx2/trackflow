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
    ISSUE_ASSIGNED(NotificationPreference::getOnIssueAssigned, NotificationPreference::getEmailOnIssueAssigned),
    ISSUE_STATUS_CHANGED(NotificationPreference::getOnIssueStatusChanged, NotificationPreference::getEmailOnIssueStatusChanged),
    ISSUE_COMMENTED(NotificationPreference::getOnIssueCommented, NotificationPreference::getEmailOnIssueCommented),
    ISSUE_RESOLVED(NotificationPreference::getOnIssueResolved, NotificationPreference::getEmailOnIssueResolved),
    MENTIONED(NotificationPreference::getOnMentioned, NotificationPreference::getEmailOnMentioned),
    /** 通用字段变更（priority, dueDate, description, sprint, parent, tags） */
    ISSUE_UPDATED(NotificationPreference::getOnIssueUpdated, NotificationPreference::getEmailOnIssueUpdated),

    // ===== Sprint 相关 =====
    SPRINT_STARTED(NotificationPreference::getOnSprintStarted, NotificationPreference::getEmailOnSprintStarted),
    SPRINT_COMPLETED(NotificationPreference::getOnSprintCompleted, NotificationPreference::getEmailOnSprintCompleted),

    // ===== Project 相关 =====
    PROJECT_MEMBER_CHANGED(NotificationPreference::getOnProjectMemberChanged, NotificationPreference::getEmailOnProjectMemberChanged),
    PROJECT_LIFECYCLE(NotificationPreference::getOnProjectLifecycle, NotificationPreference::getEmailOnProjectLifecycle),

    // ===== Date Alert 相关 =====
    DUE_DATE_APPROACHING(NotificationPreference::getOnDueDate, NotificationPreference::getEmailOnDueDate),
    OVERDUE(NotificationPreference::getOnOverdue, NotificationPreference::getEmailOnOverdue),

    // ===== Watcher 相关 =====
    /** 用户关注的工单有更新 */
    WATCHED(NotificationPreference::getOnWatchedUpdated, NotificationPreference::getEmailOnWatchedUpdated),

    // ===== Vote + Spent Time 相关（对标 YouTrack 订阅事件列表） =====
    /** 工单被投票 */
    ISSUE_VOTED(NotificationPreference::getOnIssueVoted, NotificationPreference::getEmailOnIssueVoted),
    /** 工单有工时记录变更 */
    ISSUE_SPENT_TIME(NotificationPreference::getOnIssueSpentTime, NotificationPreference::getEmailOnIssueSpentTime);

    private final Function<NotificationPreference, Boolean> extractor;
    private final Function<NotificationPreference, Boolean> emailExtractor;

    NotificationEventType(Function<NotificationPreference, Boolean> extractor,
                          Function<NotificationPreference, Boolean> emailExtractor) {
        this.extractor = extractor;
        this.emailExtractor = emailExtractor;
    }

    /**
     * 检查给定偏好中此事件类型的站内通知是否启用。
     * null 视为未设置，按默认发送处理（返回 true）。
     */
    public boolean isEnabled(NotificationPreference pref) {
        Boolean value = extractor.apply(pref);
        return !Boolean.FALSE.equals(value);
    }

    /**
     * 检查给定偏好中此事件类型的邮件通知是否启用。
     * <p>
     * 注意：此方法仅检查 per-event 邮件开关，调用方还需检查全局 emailEnabled 总开关。
     * null 视为未设置，按默认发送处理（返回 true）。
     */
    public boolean isEmailEnabled(NotificationPreference pref) {
        Boolean value = emailExtractor.apply(pref);
        return !Boolean.FALSE.equals(value);
    }
}

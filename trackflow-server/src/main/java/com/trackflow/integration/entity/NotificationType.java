package com.trackflow.integration.entity;

/**
 * 通知类型枚举——约束所有通知的 type 值。
 * <p>
 * 数据库中存储 name()（如 "issue_assigned"），前端用相同字符串做图标映射。
 * 新增通知类型时：
 * 1. 在此枚举中添加常量
 * 2. 前端 NotificationPanel.vue 的 getTypeIcon() 同步添加图标映射
 * 3. 前端 api/notification.ts 的 NotificationType 联合类型同步添加
 */
public enum NotificationType {

    // ===== Issue 相关 =====
    /** 工单被手动分配 */
    issue_assigned("工单分配"),
    /** 工单被工作流自动分配 */
    issue_auto_assigned("工单自动分配"),
    /** 工单有新评论 */
    issue_commented("工单评论"),
    /** 工单状态变更 */
    issue_status_changed("工单状态变更"),
    /** 评论中被 @提及 */
    mention("提及"),
    /** 工单被移动到其他项目 */
    issue_moved("工单移动"),
    /** 工单字段变更（priority/dueDate/description/sprint/parent/tags） */
    issue_updated("工单更新"),

    // ===== Project 相关 =====
    /** 被添加到项目 */
    member_added("成员添加"),
    /** 被移出项目 */
    member_removed("成员移除"),
    /** 项目角色变更 */
    role_changed("角色变更"),
    /** 项目负责人变更 */
    lead_changed("负责人变更"),
    /** 项目被归档 */
    project_archived("项目归档"),
    /** 项目从归档恢复 */
    project_restored("项目恢复"),
    /** 项目被删除 */
    project_deleted("项目删除"),

    // ===== Sprint 相关 =====
    /** Sprint 已启动 */
    sprint_started("Sprint启动"),
    /** Sprint 已完成 */
    sprint_completed("Sprint完成"),

    // ===== Date Alert 相关 =====
    /** 工单即将到期（到期前 N 天提醒） */
    due_date_alert("到期提醒"),
    /** 工单已逾期 */
    overdue_alert("逾期提醒");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取中文显示名称，用于通知分组/筛选等场景
     */
    public String getDisplayName() {
        return displayName;
    }
}

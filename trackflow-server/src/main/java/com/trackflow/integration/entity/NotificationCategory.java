package com.trackflow.integration.entity;

import java.util.List;
import java.util.Set;

/**
 * 通知分类——用于前端标签页筛选。
 * <p>
 * 每个分类对应一组 NotificationType，前端按分类切换标签页时传递此值。
 * 对标 YouTrack: All / Mentions & Reactions / Subscriptions / System
 */
public enum NotificationCategory {

    /**
     * 全部通知
     */
    all(List.of()),

    /**
     * @提及（评论中被@的通知）
     */
    mention(List.of(NotificationType.mention)),

    /**
     * 订阅更新（基于用户关注的工单变更通知）
     */
    subscription(List.of(
            NotificationType.issue_assigned,
            NotificationType.issue_auto_assigned,
            NotificationType.issue_commented,
            NotificationType.issue_status_changed
    )),

    /**
     * 系统通知（项目级事件、Sprint 事件、系统管理员消息）
     * 仅 system:admin 角色可见
     */
    system(List.of(
            NotificationType.member_added,
            NotificationType.member_removed,
            NotificationType.role_changed,
            NotificationType.lead_changed,
            NotificationType.project_archived,
            NotificationType.project_restored,
            NotificationType.project_deleted,
            NotificationType.sprint_started,
            NotificationType.sprint_completed
    ));

    private final List<NotificationType> types;

    NotificationCategory(List<NotificationType> types) {
        this.types = types;
    }

    /**
     * 获取该分类包含的通知类型列表。
     * all 返回空列表，表示不做类型过滤。
     */
    public List<NotificationType> getTypes() {
        return types;
    }

    /**
     * 获取该分类对应的类型名称集合（用于数据库查询 IN 条件）。
     * all 返回空集，调用方应判断为不加条件。
     */
    public Set<String> getTypeNames() {
        if (types.isEmpty()) {
            return Set.of();
        }
        return types.stream()
                .map(NotificationType::name)
                .collect(java.util.stream.Collectors.toSet());
    }
}

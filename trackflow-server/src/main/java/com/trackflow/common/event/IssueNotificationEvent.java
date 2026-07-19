package com.trackflow.common.event;

import com.trackflow.issue.entity.Issue;

/**
 * Issue 模块通知事件（sealed 接口）。
 * <p>
 * 各事件携带发送通知所需的完整上下文，
 * 避免监听器在事务提交后还需要再查询数据库。
 */
public sealed interface IssueNotificationEvent extends NotificationEvent {

    /**
     * 工单创建通知事件（通知被分配人）
     */
    record Created(Issue issue, Long creatorId) implements IssueNotificationEvent {}

    /**
     * 工单分配通知事件
     */
    record Assigned(Issue issue, Long assigneeId, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单状态变更通知事件
     */
    record StatusChanged(Issue issue, Long oldStatusId, Long newStatusId, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单评论通知事件
     */
    record Commented(Issue issue, Long commenterId) implements IssueNotificationEvent {}

    /**
     * 工单 @mention 通知事件
     */
    record Mentioned(Issue issue, String commentContent, Long commenterId) implements IssueNotificationEvent {}

    /**
     * 工单取消/废弃通知事件（状态转换到 cancelled 类别时触发）
     */
    record Cancelled(Issue issue, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单移动到其他项目通知事件
     */
    record Moved(Issue issue, Long sourceProjectId, Long targetProjectId, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单通用字段变更通知事件（priority, dueDate, description, sprint, parent, tags 等）。
     * <p>
     * 不同于 StatusChanged/Assigned 等有专门逻辑的事件，FieldUpdated 是一个通用事件，
     * 用于所有"仅需告知相关人员有变更"的字段修改。
     *
     * @param issue       变更后的 Issue 实体
     * @param fieldName   变更的字段名（如 "priority", "due_date", "description"）
     * @param oldValue    旧值（显示用，可为 null）
     * @param newValue    新值（显示用，可为 null）
     * @param operatorId  操作者 ID
     */
    record FieldUpdated(Issue issue, String fieldName, String oldValue, String newValue, Long operatorId) implements IssueNotificationEvent {}
}

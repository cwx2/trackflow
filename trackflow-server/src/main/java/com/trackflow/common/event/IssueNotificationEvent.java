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
     * <p>
     * 注意：IssueService.update() 中的多字段更新已改用 {@link MultiFieldUpdated}，
     * 本事件仅用于其他服务的单字段独立变更（如 IssueTagService）。
     *
     * @param issue       变更后的 Issue 实体
     * @param fieldName   变更的字段名（如 "priority", "due_date", "description"）
     * @param oldValue    旧值（显示用，可为 null）
     * @param newValue    新值（显示用，可为 null）
     * @param operatorId  操作者 ID
     */
    record FieldUpdated(Issue issue, String fieldName, String oldValue, String newValue, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单多字段同时变更通知事件。
     * <p>
     * 当用户在一次 API 调用中同时修改多个字段时（如优先级 + 截止日期 + 迭代），
     * 收集所有变更发布一个复合事件，确保接收人只收到一条包含全部变更详情的通知。
     * <p>
     * 与 FieldUpdated 的区别：FieldUpdated 用于其他服务的单字段独立变更，
     * MultiFieldUpdated 用于 IssueService.update() 中的批量字段变更。
     *
     * @param issue       变更后的 Issue 实体
     * @param changes     变更字段映射：fieldName → [oldValue, newValue]
     * @param operatorId  操作者 ID
     */
    record MultiFieldUpdated(Issue issue, java.util.Map<String, String[]> changes, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 附件上传通知事件。
     *
     * @param issue      所属工单
     * @param fileName   上传的文件名
     * @param operatorId 操作者 ID
     */
    record AttachmentAdded(Issue issue, String fileName, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单关联变更通知事件（创建或删除关联）。
     *
     * @param issue         操作的工单（触发侧）
     * @param targetIssueKey 目标工单 key
     * @param linkType      关联类型（如 blocks, relates_to）
     * @param added         true=创建关联, false=删除关联
     * @param operatorId    操作者 ID
     */
    record LinkChanged(Issue issue, String targetIssueKey, String linkType, boolean added, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工时记录通知事件。
     *
     * @param issue           所属工单
     * @param durationMinutes 记录的时长（分钟）
     * @param operatorId      操作者 ID（实际执行记录的人）
     */
    record TimeLogged(Issue issue, int durationMinutes, Long operatorId) implements IssueNotificationEvent {}

    /**
     * 工单恢复通知事件（从回收站还原）。
     *
     * @param issue      恢复后的工单
     * @param operatorId 操作者 ID
     */
    record Restored(Issue issue, Long operatorId) implements IssueNotificationEvent {}
}

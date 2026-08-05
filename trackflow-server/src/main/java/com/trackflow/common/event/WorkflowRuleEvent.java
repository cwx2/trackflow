package com.trackflow.common.event;

/**
 * 工作流规则引擎事件 — 在 Issue 创建/更新事务提交后发布，由 WorkflowRuleEngine 异步处理。
 * <p>
 * 通过事件解耦 IssueService 和 WorkflowRuleEngine 之间的直接依赖。
 * 仅传递 ID（不传递可变实体引用），规则引擎执行时从 DB 重新加载最新状态。
 */
public sealed interface WorkflowRuleEvent {

    /**
     * 工单创建事件 — 触发 on-create 规则
     */
    record IssueCreated(Long issueId, Long projectId) implements WorkflowRuleEvent {}

    /**
     * 字段变更事件 — 触发 on-field-changed 规则
     */
    record FieldChanged(Long issueId, Long projectId, String changedField, String oldValue) implements WorkflowRuleEvent {}

    /**
     * 评论添加事件 — 触发 comment_added 规则
     */
    record CommentAdded(Long issueId, Long projectId, Long commentId, String commentContent) implements WorkflowRuleEvent {}

    /**
     * 附件添加事件 — 触发 attachment_added 规则
     */
    record AttachmentAdded(Long issueId, Long projectId, Long attachmentId) implements WorkflowRuleEvent {}

    /**
     * 附件移除事件 — 触发 attachment_removed 规则
     */
    record AttachmentRemoved(Long issueId, Long projectId, Long attachmentId) implements WorkflowRuleEvent {}

    /**
     * 工单关联添加事件 — 触发 link_added 规则
     */
    record LinkAdded(Long issueId, Long projectId, String linkType, Long targetIssueId) implements WorkflowRuleEvent {}

    /**
     * 工单关联移除事件 — 触发 link_removed 规则
     */
    record LinkRemoved(Long issueId, Long projectId, String linkType, Long targetIssueId) implements WorkflowRuleEvent {}

    /**
     * 工时记录添加事件 — 触发 work_item_added 规则
     */
    record WorkItemAdded(Long issueId, Long projectId, Long timeEntryId) implements WorkflowRuleEvent {}

    /**
     * 工时记录删除事件 — 触发 work_item_deleted 规则
     */
    record WorkItemDeleted(Long issueId, Long projectId, Long timeEntryId) implements WorkflowRuleEvent {}

    /**
     * 工单变为已解决事件 — 状态转换到 isClosed=true 时触发
     */
    record IssueResolved(Long issueId, Long projectId) implements WorkflowRuleEvent {}

    /**
     * 工单变为未解决事件 — 状态从 isClosed=true 转回 isClosed=false 时触发
     */
    record IssueUnresolved(Long issueId, Long projectId) implements WorkflowRuleEvent {}
}

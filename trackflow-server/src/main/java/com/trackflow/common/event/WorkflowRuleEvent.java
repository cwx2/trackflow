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
}

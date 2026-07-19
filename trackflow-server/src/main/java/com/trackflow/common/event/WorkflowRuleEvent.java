package com.trackflow.common.event;

import com.trackflow.issue.entity.Issue;

/**
 * 工作流规则引擎事件 — 在 Issue 创建/更新后发布，由 WorkflowRuleEngine 监听处理。
 * 通过事件解耦 IssueService 和 WorkflowRuleEngine 之间的直接依赖。
 */
public sealed interface WorkflowRuleEvent {

    /**
     * 工单创建事件 — 触发 on-create 规则
     */
    record IssueCreated(Issue issue) implements WorkflowRuleEvent {}

    /**
     * 字段变更事件 — 触发 on-field-changed 规则
     */
    record FieldChanged(Issue issue, String changedField, String oldValue) implements WorkflowRuleEvent {}
}

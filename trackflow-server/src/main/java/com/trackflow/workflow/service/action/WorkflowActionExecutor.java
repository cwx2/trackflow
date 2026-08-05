package com.trackflow.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.workflow.entity.WorkflowRule;

/**
 * 工作流动作执行器接口 — 策略模式核心抽象。
 * <p>
 * 每种动作类型（set_field、add_tag、create_issue 等）实现此接口，
 * 通过 Spring 自动发现机制注册到 {@link WorkflowActionRegistry}。
 * 新增动作类型只需创建新的 @Component 实现，无需修改引擎核心。
 *
 * @author TrackFlow
 * @since 1.0
 */
public interface WorkflowActionExecutor {

    /**
     * 本执行器处理的动作类型标识（对应 action JSON 中的 "type" 字段）。
     * <p>
     * 例如："set_field"、"add_tag"、"create_issue"
     */
    String actionType();

    /**
     * 执行动作。
     *
     * @param actionConfig 动作配置 JSON 节点（单个 action block）
     * @param issue        当前工单实体（可被修改）
     * @param rule         触发执行的工作流规则
     * @param context      动作执行上下文（跨 action block 共享状态）
     * @return 执行结果
     */
    ActionResult execute(JsonNode actionConfig, Issue issue, WorkflowRule rule,
                         ActionExecutionContext context);
}

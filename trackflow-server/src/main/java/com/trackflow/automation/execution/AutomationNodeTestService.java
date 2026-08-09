package com.trackflow.automation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.execution.dto.NodeTestDTO;
import com.trackflow.automation.execution.vo.NodeTestResultVO;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 节点级试运行：复用节点生产执行器，但不创建工作流执行记录，也不会修改工作流定义。
 * 对需要持久化编排上下文的控制节点返回可验证的预演结果，避免伪造一次真实审批或子工作流执行。
 */
@Service
@RequiredArgsConstructor
public class AutomationNodeTestService {
    private static final Set<String> SIDE_EFFECT_NODE_TYPES = Set.of(
            "trackflow-issue-comment", "trackflow-issue-update", "trackflow-issue-transition",
            "http-request", "code", "cli-agent", "role-agent");
    private static final Set<String> ORCHESTRATION_NODE_TYPES = Set.of(
            "approval", "loop", "sub-workflow");

    private final AutomationWorkflowService workflowService;
    private final NodeRegistry nodeRegistry;
    private final ObjectMapper objectMapper;
    private final AutomationActorRunner actorRunner;

    public NodeTestResultVO test(Long workflowId, String nodeId, NodeTestDTO request, Long requestedBy) {
        AutomationWorkflow workflow = workflowService.getById(workflowId);
        WorkflowNodeModel node = findNode(workflow, nodeId);
        NodeExecutor executor = nodeRegistry.getExecutor(node.type());
        if (executor == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "节点没有可用执行器: " + node.type());
        }
        Map<String, Object> overrides = request != null && request.getInputOverrides() != null
                ? request.getInputOverrides() : Map.of();
        Long actorUserId = workflow.getActorUserId() != null ? workflow.getActorUserId() : requestedBy;
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "请先配置工作流执行身份后再试运行节点");
        }
        ExecutionContext context = new ExecutionContext(Map.of(), Map.of(), actorUserId);
        Map<String, Object> resolvedInput = context.resolveInputs(node.inputs());
        resolvedInput.putAll(overrides);

        if (SIDE_EFFECT_NODE_TYPES.contains(node.type())
                && !Boolean.TRUE.equals(request != null ? request.getConfirmSideEffects() : null)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "此节点试运行会产生外部副作用，请确认后再执行");
        }
        if (ORCHESTRATION_NODE_TYPES.contains(node.type())) {
            return NodeTestResultVO.builder()
                    .nodeId(node.id()).status("simulated").input(resolvedInput).output(Map.of())
                    .message("已完成节点配置与输入预演；该节点依赖持久化编排上下文，请通过完整工作流试运行验证挂起、循环或子流程恢复行为。")
                    .durationMs(0L).build();
        }

        long startedAt = System.nanoTime();
        try {
            Map<String, Object> output = actorRunner.runAs(actorUserId,
                    () -> execute(executor, resolvedInput, node, context));
            return NodeTestResultVO.builder()
                    .nodeId(node.id()).status("success").input(resolvedInput).output(output)
                    .durationMs((System.nanoTime() - startedAt) / 1_000_000).build();
        } catch (NodeTestExecutionException exception) {
            return NodeTestResultVO.builder()
                    .nodeId(node.id()).status("failed").input(resolvedInput).output(Map.of())
                    .error(exception.nodeException.getMessage())
                    .durationMs((System.nanoTime() - startedAt) / 1_000_000).build();
        } catch (RuntimeException exception) {
            return NodeTestResultVO.builder()
                    .nodeId(node.id()).status("failed").input(resolvedInput).output(Map.of())
                    .error(exception.getMessage() != null ? exception.getMessage() : "节点试运行发生未预期错误")
                    .durationMs((System.nanoTime() - startedAt) / 1_000_000).build();
        }
    }

    private Map<String, Object> execute(NodeExecutor executor, Map<String, Object> inputs,
                                        WorkflowNodeModel node, ExecutionContext context) {
        try {
            return executor.execute(inputs, node, context);
        } catch (NodeExecutionException exception) {
            throw new NodeTestExecutionException(exception);
        }
    }

    private WorkflowNodeModel findNode(AutomationWorkflow workflow, String nodeId) {
        try {
            WorkflowDefinitionModel definition = objectMapper.readValue(workflow.getDefinition(), WorkflowDefinitionModel.class);
            return definition.nodes().stream().filter(node -> node.id().equals(nodeId)).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流中不存在该节点"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "工作流定义格式错误，无法试运行节点");
        }
    }

    private static class NodeTestExecutionException extends RuntimeException {
        private final NodeExecutionException nodeException;
        NodeTestExecutionException(NodeExecutionException nodeException) {
            super(nodeException);
            this.nodeException = nodeException;
        }
    }
}

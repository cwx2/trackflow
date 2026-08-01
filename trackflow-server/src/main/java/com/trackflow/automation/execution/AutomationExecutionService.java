package com.trackflow.automation.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.entity.AutomationNodeExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.execution.mapper.AutomationNodeExecutionMapper;
import com.trackflow.automation.approval.entity.AutomationApproval;
import com.trackflow.automation.approval.mapper.AutomationApprovalMapper;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** 工作流执行应用服务，统一手动执行和队列执行的创建、冻结定义与取消语义。 */
@Service
@RequiredArgsConstructor
public class AutomationExecutionService {
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "queued", "running", "waiting_timer", "waiting_event",
            "waiting_approval", "retrying", "paused");

    private final AutomationWorkflowService workflowService;
    private final AutomationExecutionMapper executionMapper;
    private final DAGExecutor dagExecutor;
    private final SseNotifier sseNotifier;
    private final ObjectMapper objectMapper;
    private final AutomationNodeExecutionMapper nodeExecutionMapper;
    private final AutomationApprovalMapper approvalMapper;

    public Long startDraft(Long automationId, Map<String, Object> inputs, Long requestedActorUserId) {
        AutomationWorkflow workflow = workflowService.getById(automationId);
        return start(workflow, workflow.getDefinition(), inputs, requestedActorUserId,
                null, null, null);
    }

    public Long startPublished(Long automationId, Map<String, Object> inputs, String correlationId) {
        AutomationWorkflow workflow = workflowService.getById(automationId);
        if (!"published".equals(workflow.getStatus()) || workflow.getPublishedDefinition() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "工作流尚未发布或已停用");
        }
        assertCapacity(workflow);
        return start(workflow, workflow.getPublishedDefinition(), inputs,
                workflow.getActorUserId(), correlationId, null, null);
    }

    public Long startChild(Long automationId, Map<String, Object> inputs,
                           Long parentExecutionId, String parentNodeId) {
        AutomationWorkflow workflow = workflowService.getById(automationId);
        if (!"published".equals(workflow.getStatus()) || workflow.getPublishedDefinition() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "子工作流必须先发布");
        }
        AutomationExecution parent = requireExecution(parentExecutionId);
        assertChildCallAllowed(workflow.getId(), parent, 5);
        return start(workflow, workflow.getPublishedDefinition(), inputs,
                workflow.getActorUserId() != null ? workflow.getActorUserId() : parent.getActorUserId(),
                "child:" + parentExecutionId + ":" + parentNodeId + ":" + System.nanoTime(),
                parentExecutionId, parentNodeId);
    }

    public boolean hasCapacity(Long automationId) {
        AutomationWorkflow workflow = workflowService.getById(automationId);
        long active = executionMapper.selectCount(new LambdaQueryWrapper<AutomationExecution>()
                .eq(AutomationExecution::getAutomationId, automationId)
                .in(AutomationExecution::getStatus, ACTIVE_STATUSES));
        return active < Math.max(1, workflow.getMaxConcurrent() != null
                ? workflow.getMaxConcurrent() : 1);
    }

    public void cancel(Long executionId) {
        AutomationExecution execution = requireExecution(executionId);
        AutomationExecutionStatus status = AutomationExecutionStatus.fromValue(execution.getStatus());
        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "当前执行已结束，无法取消");
        }
        if (!dagExecutor.cancel(executionId)) {
            AutomationExecution update = new AutomationExecution();
            update.setId(executionId);
            update.setStatus(AutomationExecutionStatus.CANCELLED.getValue());
            update.setFinishedAt(LocalDateTime.now());
            executionMapper.updateById(update);
            nodeExecutionMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AutomationNodeExecution>()
                            .set(AutomationNodeExecution::getStatus, AutomationNodeStatus.CANCELLED.getValue())
                            .set(AutomationNodeExecution::getOutcome, "cancelled")
                            .set(AutomationNodeExecution::getFinishedAt, LocalDateTime.now())
                            .eq(AutomationNodeExecution::getExecutionId, executionId)
                            .in(AutomationNodeExecution::getStatus,
                                    AutomationNodeStatus.RUNNING.getValue(),
                                    AutomationNodeStatus.WAITING.getValue(),
                                    AutomationNodeStatus.RETRYING.getValue()));
            approvalMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AutomationApproval>()
                            .set(AutomationApproval::getStatus, "cancelled")
                            .set(AutomationApproval::getDecidedAt, LocalDateTime.now())
                            .eq(AutomationApproval::getExecutionId, executionId)
                            .eq(AutomationApproval::getStatus, "pending"));
            List<AutomationExecution> children = executionMapper.selectList(
                    new LambdaQueryWrapper<AutomationExecution>()
                            .eq(AutomationExecution::getParentExecutionId, executionId)
                            .in(AutomationExecution::getStatus, ACTIVE_STATUSES));
            children.forEach(child -> cancel(child.getId()));
            sseNotifier.send(executionId, SseNotifier.workflowCancelled());
            sseNotifier.complete(executionId);
        }
    }

    public AutomationExecution requireExecution(Long executionId) {
        AutomationExecution execution = executionMapper.selectById(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "执行记录不存在: " + executionId);
        }
        return execution;
    }

    private Long start(AutomationWorkflow workflow, String definitionJson,
                       Map<String, Object> inputs, Long requestedActorUserId,
                       String correlationId, Long parentExecutionId, String parentNodeId) {
        WorkflowDefinitionModel definition;
        try {
            definition = objectMapper.readValue(definitionJson != null ? definitionJson : "{}",
                    WorkflowDefinitionModel.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "工作流定义格式错误: " + exception.getMessage());
        }
        Long actorUserId = workflow.getActorUserId() != null
                ? workflow.getActorUserId() : requestedActorUserId;
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "工作流未配置执行身份");
        }

        AutomationExecution execution = new AutomationExecution();
        execution.setAutomationId(workflow.getId());
        execution.setStatus(AutomationExecutionStatus.RUNNING.getValue());
        execution.setStartedAt(LocalDateTime.now());
        execution.setCreatedBy(requestedActorUserId);
        execution.setActorUserId(actorUserId);
        execution.setHeartbeatAt(LocalDateTime.now());
        execution.setCorrelationId(correlationId);
        execution.setDefinitionVersion(workflow.getVersion() != null ? workflow.getVersion() : 1);
        execution.setDefinitionSnapshot(definitionJson);
        execution.setParentExecutionId(parentExecutionId);
        execution.setParentNodeId(parentNodeId);
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        try {
            execution.setInput(objectMapper.writeValueAsString(safeInputs));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "触发输入无法序列化");
        }
        executionMapper.insert(execution);
        dagExecutor.executeAsync(definition, execution.getId(), safeInputs, actorUserId);
        return execution.getId();
    }

    private void assertChildCallAllowed(Long targetAutomationId,
                                        AutomationExecution parent,
                                        int maxDepth) {
        int depth = 1;
        AutomationExecution cursor = parent;
        while (true) {
            if (targetAutomationId.equals(cursor.getAutomationId())) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "子工作流调用链存在递归，目标工作流已在上游执行链中");
            }
            if (cursor.getParentExecutionId() == null) {
                return;
            }
            depth++;
            if (depth > maxDepth) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "子工作流嵌套深度不能超过 " + maxDepth);
            }
            cursor = requireExecution(cursor.getParentExecutionId());
        }
    }

    private void assertCapacity(AutomationWorkflow workflow) {
        if (!hasCapacity(workflow.getId())) {
            String message = "skip".equals(workflow.getConcurrencyMode())
                    ? "工作流已有执行，已按并发策略跳过" : "工作流并发已满，请稍后重试";
            throw new BusinessException(ErrorCode.INVALID_STATE, message);
        }
    }
}

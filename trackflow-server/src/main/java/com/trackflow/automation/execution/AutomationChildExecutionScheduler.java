package com.trackflow.automation.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import com.trackflow.common.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检测子工作流终态并恢复父工作流。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationChildExecutionScheduler {
    private final AutomationExecutionMapper executionMapper;
    private final DAGExecutor dagExecutor;
    private final DistributedLockService lockService;
    private final ObjectMapper objectMapper;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    @Scheduled(fixedDelay = 1000)
    public void resumeParents() {
        if (!runtimeCoordinator.shouldRunWorkers()) return;
        lockService.executeWithLock("automation_child_resume", this::scanParents);
    }

    private void scanParents() {
        List<AutomationExecution> parents = executionMapper.selectList(
                new LambdaQueryWrapper<AutomationExecution>()
                        .eq(AutomationExecution::getStatus,
                                AutomationExecutionStatus.WAITING_EVENT.getValue())
                        .orderByAsc(AutomationExecution::getHeartbeatAt)
                        .last("LIMIT 100"));
        for (AutomationExecution parent : parents) resumeIfChildFinished(parent);
    }

    private void resumeIfChildFinished(AutomationExecution parent) {
        try {
            WorkflowExecutionCheckpoint checkpoint = objectMapper.readValue(
                    parent.getCheckpoint(), WorkflowExecutionCheckpoint.class);
            Object childValue = checkpoint.getWaitingMetadata().get("childExecutionId");
            if (childValue == null) return;
            Long childId = childValue instanceof Number number
                    ? number.longValue() : Long.valueOf(childValue.toString());
            AutomationExecution child = executionMapper.selectById(childId);
            if (child == null) {
                failParent(parent.getId(), "子工作流执行记录不存在: " + childId);
                return;
            }
            AutomationExecutionStatus childStatus = AutomationExecutionStatus.fromValue(child.getStatus());
            if (!childStatus.isTerminal()) return;

            Object childOutput = child.getOutput() == null ? Map.of()
                    : objectMapper.readValue(child.getOutput(), Object.class);
            Map<String, Object> waitingResult = new LinkedHashMap<>();
            waitingResult.put("output", childOutput);
            waitingResult.put("status", childStatus.getValue());
            waitingResult.put("success", childStatus == AutomationExecutionStatus.SUCCESS);
            waitingResult.put("error", child.getErrorMessage() != null ? child.getErrorMessage() : "");
            checkpoint.setWaitingResult(waitingResult);
            String checkpointJson = objectMapper.writeValueAsString(checkpoint);

            int updated = executionMapper.update(null, new LambdaUpdateWrapper<AutomationExecution>()
                    .set(AutomationExecution::getStatus, AutomationExecutionStatus.RUNNING.getValue())
                    .set(AutomationExecution::getCheckpoint, checkpointJson)
                    .set(AutomationExecution::getHeartbeatAt, LocalDateTime.now())
                    .eq(AutomationExecution::getId, parent.getId())
                    .eq(AutomationExecution::getStatus,
                            AutomationExecutionStatus.WAITING_EVENT.getValue()));
            if (updated != 1) return;

            WorkflowDefinitionModel definition = objectMapper.readValue(
                    parent.getDefinitionSnapshot(), WorkflowDefinitionModel.class);
            Map<String, Object> inputs = parent.getInput() == null ? Map.of()
                    : objectMapper.readValue(parent.getInput(), new TypeReference<>() {});
            dagExecutor.resumeAsync(definition, parent.getId(), inputs,
                    parent.getActorUserId(), checkpointJson);
        } catch (Exception exception) {
            log.error("恢复父工作流失败: executionId={}", parent.getId(), exception);
            failParent(parent.getId(), "恢复子工作流结果失败: " + exception.getMessage());
        }
    }

    private void failParent(Long parentId, String error) {
        executionMapper.update(null, new LambdaUpdateWrapper<AutomationExecution>()
                .set(AutomationExecution::getStatus, AutomationExecutionStatus.FAILED.getValue())
                .set(AutomationExecution::getErrorMessage, error)
                .set(AutomationExecution::getFinishedAt, LocalDateTime.now())
                .eq(AutomationExecution::getId, parentId)
                .eq(AutomationExecution::getStatus,
                        AutomationExecutionStatus.WAITING_EVENT.getValue()));
    }
}

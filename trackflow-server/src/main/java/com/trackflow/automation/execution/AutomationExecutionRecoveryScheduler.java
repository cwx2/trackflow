package com.trackflow.automation.execution;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动化执行恢复调度器。
 *
 * <p>扫描到期的持久化等待任务，以数据库状态比较完成原子领取。即使应用在等待期间重启，
 * 任务仍可从最近检查点继续，而不会占用 Web 或异步工作线程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationExecutionRecoveryScheduler {
    private static final int RESUME_BATCH_SIZE = 50;

    private final AutomationExecutionMapper executionMapper;
    private final DAGExecutor dagExecutor;
    private final DistributedLockService distributedLockService;
    private final ObjectMapper objectMapper;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    @Scheduled(fixedDelay = 1000)
    public void resumeDueExecutions() {
        if (!runtimeCoordinator.shouldRunWorkers()) return;
        distributedLockService.executeWithLock(
                "automation_execution_resume", this::scanAndResumeDueExecutions);
    }

    private void scanAndResumeDueExecutions() {
        List<AutomationExecution> executions = executionMapper.selectList(
                new LambdaQueryWrapper<AutomationExecution>()
                        .in(AutomationExecution::getStatus, Set.of(
                                AutomationExecutionStatus.WAITING_TIMER.getValue(),
                                AutomationExecutionStatus.RETRYING.getValue()))
                        .le(AutomationExecution::getWakeUpAt, LocalDateTime.now())
                        .orderByAsc(AutomationExecution::getWakeUpAt)
                        .last("LIMIT " + RESUME_BATCH_SIZE)
        );
        for (AutomationExecution execution : executions) {
            resumeExecution(execution);
        }
        resumeStaleRunningExecutions();
    }

    private void resumeStaleRunningExecutions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        List<AutomationExecution> stale = executionMapper.selectList(
                new LambdaQueryWrapper<AutomationExecution>()
                        .eq(AutomationExecution::getStatus, AutomationExecutionStatus.RUNNING.getValue())
                        .le(AutomationExecution::getHeartbeatAt, cutoff)
                        .orderByAsc(AutomationExecution::getHeartbeatAt)
                        .last("LIMIT " + RESUME_BATCH_SIZE));
        for (AutomationExecution execution : stale) {
            if (dagExecutor.isRunningLocally(execution.getId())) continue;
            AutomationExecution claim = new AutomationExecution();
            claim.setHeartbeatAt(LocalDateTime.now());
            int updated = executionMapper.update(claim,
                    new LambdaUpdateWrapper<AutomationExecution>()
                            .eq(AutomationExecution::getId, execution.getId())
                            .eq(AutomationExecution::getStatus,
                                    AutomationExecutionStatus.RUNNING.getValue())
                            .le(AutomationExecution::getHeartbeatAt, cutoff));
            if (updated == 1 && !repairSuspendedState(execution)) restoreClaimedExecution(execution);
        }
    }

    private boolean repairSuspendedState(AutomationExecution execution) {
        try {
            WorkflowExecutionCheckpoint checkpoint = objectMapper.readValue(
                    execution.getCheckpoint(), WorkflowExecutionCheckpoint.class);
            if (checkpoint.getWaitingNodeId() == null || !checkpoint.getWaitingResult().isEmpty()) return false;
            String kind = checkpoint.getWaitingKind();
            AutomationExecutionStatus target;
            if ("approval".equals(kind)) target = AutomationExecutionStatus.WAITING_APPROVAL;
            else if ("subworkflow".equals(kind) || "loop".equals(kind)) {
                target = AutomationExecutionStatus.WAITING_EVENT;
            } else if ("timer".equals(kind)) target = AutomationExecutionStatus.WAITING_TIMER;
            else if ("retry".equals(kind) || "loop_interval".equals(kind)) {
                target = AutomationExecutionStatus.RETRYING;
            } else return false;

            if ((target == AutomationExecutionStatus.WAITING_TIMER
                    || target == AutomationExecutionStatus.RETRYING)
                    && (checkpoint.getWaitingUntil() == null
                    || !checkpoint.getWaitingUntil().isAfter(LocalDateTime.now()))) {
                return false;
            }
            AutomationExecution repair = new AutomationExecution();
            repair.setId(execution.getId());
            repair.setStatus(target.getValue());
            repair.setWakeUpAt(checkpoint.getWaitingUntil());
            repair.setHeartbeatAt(LocalDateTime.now());
            executionMapper.updateById(repair);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void resumeExecution(AutomationExecution execution) {
        AutomationExecution update = new AutomationExecution();
        update.setStatus(AutomationExecutionStatus.RUNNING.getValue());
        update.setHeartbeatAt(LocalDateTime.now());
        String claimedStatus = execution.getStatus();
        int updated = executionMapper.update(update,
                new LambdaUpdateWrapper<AutomationExecution>()
                        .eq(AutomationExecution::getId, execution.getId())
                        .eq(AutomationExecution::getStatus, claimedStatus));
        if (updated == 0) return;

        restoreClaimedExecution(execution);
    }

    private void restoreClaimedExecution(AutomationExecution execution) {
        try {
            WorkflowDefinitionModel definition = objectMapper.readValue(
                    execution.getDefinitionSnapshot(), WorkflowDefinitionModel.class);
            Map<String, Object> inputs = execution.getInput() == null
                    ? Map.of()
                    : objectMapper.readValue(execution.getInput(), new TypeReference<>() {});
            dagExecutor.resumeAsync(definition, execution.getId(), inputs,
                    execution.getActorUserId(), execution.getCheckpoint());
        } catch (Exception exception) {
            log.error("恢复自动化执行失败: executionId={}", execution.getId(), exception);
            AutomationExecution failed = new AutomationExecution();
            failed.setId(execution.getId());
            failed.setStatus(AutomationExecutionStatus.FAILED.getValue());
            failed.setErrorMessage("恢复执行失败: " + exception.getMessage());
            failed.setFinishedAt(LocalDateTime.now());
            executionMapper.updateById(failed);
        }
    }
}

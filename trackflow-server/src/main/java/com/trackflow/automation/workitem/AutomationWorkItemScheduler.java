package com.trackflow.automation.workitem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.AutomationExecutionService;
import com.trackflow.automation.execution.AutomationExecutionStatus;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.workitem.entity.AutomationWorkItem;
import com.trackflow.automation.service.AutomationWorkflowService;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.Map;

/** 持续领取待办需求并启动已发布工作流，同时回收执行结果。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationWorkItemScheduler {
    private static final int CLAIM_BATCH_SIZE = 10;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    private final AutomationWorkItemService workItemService;
    private final AutomationExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final AutomationWorkflowService workflowService;
    private final AutomationRuntimeCoordinator runtimeCoordinator;

    @Scheduled(fixedDelay = 2000)
    public void dispatch() {
        if (!runtimeCoordinator.shouldRunWorkers()) return;
        reconcileExecutions();
        for (int i = 0; i < CLAIM_BATCH_SIZE; i++) {
            AutomationWorkItem item = workItemService.claim(workerId);
            if (item == null) return;
            if (!executionService.hasCapacity(item.getAutomationId())) {
                if ("skip".equals(workflowService.getById(item.getAutomationId()).getConcurrencyMode())) {
                    workItemService.markCancelled(item.getId());
                } else {
                    workItemService.defer(item, 3);
                }
                continue;
            }
            try {
                Map<String, Object> payload = item.getPayload() == null ? Map.of()
                        : objectMapper.readValue(item.getPayload(), new TypeReference<>() {});
                Long executionId = executionService.startPublished(
                        item.getAutomationId(), payload, item.getCorrelationId());
                workItemService.markRunning(item.getId(), item.getLeaseToken(), executionId);
            } catch (Exception exception) {
                log.error("自动化工作启动失败: workItemId={}", item.getId(), exception);
                workItemService.markFailed(item, exception.getMessage());
            }
        }
    }

    private void reconcileExecutions() {
        for (AutomationWorkItem item : workItemService.listRunning(100)) {
            AutomationExecution execution;
            try {
                execution = executionService.requireExecution(item.getExecutionId());
            } catch (RuntimeException exception) {
                workItemService.markFailed(item, exception.getMessage());
                continue;
            }
            AutomationExecutionStatus status;
            try {
                status = AutomationExecutionStatus.fromValue(execution.getStatus());
            } catch (IllegalArgumentException exception) {
                workItemService.markFailed(item, exception.getMessage());
                continue;
            }
            if (status == AutomationExecutionStatus.SUCCESS) {
                workItemService.markCompleted(item.getId());
            } else if (status == AutomationExecutionStatus.FAILED) {
                workItemService.markFailed(item, execution.getErrorMessage());
            } else if (status == AutomationExecutionStatus.CANCELLED) {
                workItemService.markCancelled(item.getId());
            }
        }
    }
}

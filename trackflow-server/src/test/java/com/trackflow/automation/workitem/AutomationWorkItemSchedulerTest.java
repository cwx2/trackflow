package com.trackflow.automation.workitem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.AutomationExecutionService;
import com.trackflow.automation.runtime.AutomationRuntimeCoordinator;
import com.trackflow.automation.service.AutomationWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationWorkItemSchedulerTest {
    @Mock private AutomationWorkItemService workItemService;
    @Mock private AutomationExecutionService executionService;
    @Mock private ObjectMapper objectMapper;
    @Mock private AutomationWorkflowService workflowService;
    @Mock private AutomationRuntimeCoordinator runtimeCoordinator;

    @Test
    void dispatchReturnsBeforeAnyDatabaseBackedServiceWhenRuntimeSleeps() {
        when(runtimeCoordinator.shouldRunWorkers()).thenReturn(false);
        AutomationWorkItemScheduler scheduler = new AutomationWorkItemScheduler(
                workItemService, executionService, objectMapper, workflowService, runtimeCoordinator);

        scheduler.dispatch();

        verifyNoInteractions(workItemService, executionService, objectMapper, workflowService);
    }
}

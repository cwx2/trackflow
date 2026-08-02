package com.trackflow.automation.runtime;

import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.trigger.mapper.AutomationEventInboxMapper;
import com.trackflow.automation.workitem.mapper.AutomationWorkItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRuntimeCoordinatorTest {
    @Mock private AutomationWorkflowMapper workflowMapper;
    @Mock private AutomationExecutionMapper executionMapper;
    @Mock private AutomationWorkItemMapper workItemMapper;
    @Mock private AutomationEventInboxMapper eventInboxMapper;

    private AutomationRuntimeCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new AutomationRuntimeCoordinator(
                workflowMapper, executionMapper, workItemMapper, eventInboxMapper);
    }

    @Test
    void idleRuntimeDoesNotQueryDatabaseOnScheduledCheck() {
        coordinator.sleepWhenDrained();

        assertThat(coordinator.shouldRunWorkers()).isFalse();
        verifyNoInteractions(workflowMapper, executionMapper, workItemMapper, eventInboxMapper);
    }

    @Test
    void manualWorkflowActivationDoesNotWakePollingWorkers() {
        coordinator.onWorkflowStarted(workflow(1L, "manual"));

        assertThat(coordinator.shouldRunTrigger("manual")).isTrue();
        assertThat(coordinator.shouldRunWorkers()).isFalse();
    }

    @Test
    void automaticWorkflowActivationArmsTriggerWithoutPollingUntilWorkArrives() {
        coordinator.onWorkflowStarted(workflow(2L, "issue_created"));
        assertThat(coordinator.shouldRunTrigger("issue_created")).isTrue();
        assertThat(coordinator.shouldRunWorkers()).isFalse();

        coordinator.wakeWorkers();
        assertThat(coordinator.shouldRunWorkers()).isTrue();

        coordinator.onWorkflowStopped(2L);
        when(workItemMapper.selectCount(any())).thenReturn(0L);
        when(executionMapper.selectCount(any())).thenReturn(0L);
        when(eventInboxMapper.selectCount(any())).thenReturn(0L);
        coordinator.sleepWhenDrained();

        assertThat(coordinator.shouldRunTrigger("issue_created")).isFalse();
        assertThat(coordinator.shouldRunWorkers()).isFalse();
    }

    @Test
    void transactionalWakeIsDeferredUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            coordinator.wakeWorkersAfterCommit();
            assertThat(coordinator.shouldRunWorkers()).isFalse();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(coordinator.shouldRunWorkers()).isTrue();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AutomationWorkflow workflow(Long id, String triggerType) {
        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(id);
        workflow.setTriggerType(triggerType);
        workflow.setRuntimeEnabled(true);
        return workflow;
    }
}

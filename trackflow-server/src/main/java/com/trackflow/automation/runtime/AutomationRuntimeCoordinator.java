package com.trackflow.automation.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.automation.entity.AutomationWorkflow;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.mapper.AutomationWorkflowMapper;
import com.trackflow.automation.trigger.entity.AutomationEventInbox;
import com.trackflow.automation.trigger.mapper.AutomationEventInboxMapper;
import com.trackflow.automation.workitem.entity.AutomationWorkItem;
import com.trackflow.automation.workitem.mapper.AutomationWorkItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自动化运行门闩。
 *
 * <p>没有人工启动的自动工作流、也没有待恢复任务时，所有轮询调度器仅做一次内存判断，
 * 不访问数据库和分布式锁。应用重启时只恢复一次已启用工作流和未完成任务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationRuntimeCoordinator {
    private static final Set<String> ACTIVE_EXECUTION_STATUSES = Set.of(
            "queued", "running", "waiting_timer", "waiting_event",
            "waiting_approval", "retrying", "paused");
    private static final Set<String> ACTIVE_WORK_ITEM_STATES = Set.of("queued", "leased", "running");

    private final AutomationWorkflowMapper workflowMapper;
    private final AutomationExecutionMapper executionMapper;
    private final AutomationWorkItemMapper workItemMapper;
    private final AutomationEventInboxMapper eventInboxMapper;

    private final ConcurrentHashMap<Long, String> activeWorkflowTriggers = new ConcurrentHashMap<>();
    private final AtomicBoolean workersAwake = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRuntimeState() {
        List<AutomationWorkflow> activeWorkflows = workflowMapper.selectList(
                new LambdaQueryWrapper<AutomationWorkflow>()
                        .eq(AutomationWorkflow::getStatus, "published")
                        .eq(AutomationWorkflow::getRuntimeEnabled, true));
        activeWorkflowTriggers.clear();
        activeWorkflows.forEach(workflow -> activeWorkflowTriggers.put(
                workflow.getId(), workflow.getTriggerType()));
        boolean pending = hasPendingWork();
        workersAwake.set(pending);
        log.info("自动化运行态已恢复: activeWorkflows={}, workersAwake={}",
                activeWorkflows.size(), workersAwake.get());
    }

    public boolean shouldRunWorkers() {
        return workersAwake.get();
    }

    public boolean shouldRunTrigger(String triggerType) {
        return activeWorkflowTriggers.containsValue(triggerType);
    }

    public void onWorkflowStarted(AutomationWorkflow workflow) {
        activeWorkflowTriggers.put(workflow.getId(), workflow.getTriggerType());
        log.info("自动化已人工启动: workflowId={}, triggerType={}",
                workflow.getId(), workflow.getTriggerType());
    }

    public void onWorkflowStopped(Long workflowId) {
        activeWorkflowTriggers.remove(workflowId);
        log.info("自动化已停止接收新触发: workflowId={}", workflowId);
    }

    public void wakeWorkers() {
        workersAwake.set(true);
    }

    /**
     * 数据在事务提交成功后才对调度线程可见，避免先唤醒、后提交造成任务滞留。
     */
    public void wakeWorkersAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            wakeWorkers();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                wakeWorkers();
            }
        });
    }

    /**
     * 仅在系统曾被任务唤醒时检查排空状态。
     * 一旦排空，后续周期只做内存判断，不再查库。
     */
    @Scheduled(fixedDelayString = "${trackflow.automation.runtime-idle-check-ms:30000}")
    public void sleepWhenDrained() {
        if (!workersAwake.compareAndSet(true, false)) {
            return;
        }
        if (hasPendingWork()) {
            workersAwake.set(true);
            return;
        }
        log.info("自动化任务已排空，后台执行器进入休眠");
    }

    private boolean hasPendingWork() {
        if (workItemMapper.selectCount(new LambdaQueryWrapper<AutomationWorkItem>()
                .in(AutomationWorkItem::getState, ACTIVE_WORK_ITEM_STATES)) > 0) {
            return true;
        }
        if (executionMapper.selectCount(new LambdaQueryWrapper<AutomationExecution>()
                .in(AutomationExecution::getStatus, ACTIVE_EXECUTION_STATUSES)) > 0) {
            return true;
        }
        return eventInboxMapper.selectCount(new LambdaQueryWrapper<AutomationEventInbox>()
                .isNull(AutomationEventInbox::getProcessedAt)) > 0;
    }
}

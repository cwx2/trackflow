package com.trackflow.automation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.entity.AutomationNodeExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.execution.mapper.AutomationNodeExecutionMapper;
import com.trackflow.automation.approval.entity.AutomationApproval;
import com.trackflow.automation.approval.mapper.AutomationApprovalMapper;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DAGExecutor {

    private static final Logger log = LoggerFactory.getLogger(DAGExecutor.class);

    private final NodeRegistry nodeRegistry;
    private final DAGBuilder dagBuilder;
    private final WorkflowExecutionPlanner executionPlanner;
    private final SseNotifier sseNotifier;
    private final AutomationExecutionMapper executionMapper;
    private final AutomationNodeExecutionMapper nodeExecutionMapper;
    private final ObjectMapper objectMapper;
    private final AutomationActorRunner actorRunner;
    private final AutomationApprovalMapper approvalMapper;
    private final ObjectProvider<AutomationExecutionService> executionServiceProvider;
    private final Map<Long, ExecutionContext> runningContexts = new ConcurrentHashMap<>();

    public DAGExecutor(NodeRegistry nodeRegistry, DAGBuilder dagBuilder,
                       WorkflowExecutionPlanner executionPlanner, SseNotifier sseNotifier,
                       AutomationExecutionMapper executionMapper,
                       AutomationNodeExecutionMapper nodeExecutionMapper,
                       ObjectMapper objectMapper, AutomationActorRunner actorRunner,
                       AutomationApprovalMapper approvalMapper,
                       ObjectProvider<AutomationExecutionService> executionServiceProvider) {
        this.nodeRegistry = nodeRegistry;
        this.dagBuilder = dagBuilder;
        this.executionPlanner = executionPlanner;
        this.sseNotifier = sseNotifier;
        this.executionMapper = executionMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.objectMapper = objectMapper;
        this.actorRunner = actorRunner;
        this.approvalMapper = approvalMapper;
        this.executionServiceProvider = executionServiceProvider;
    }

    @Async
    public void executeAsync(WorkflowDefinitionModel def, Long executionId,
                             Map<String, Object> triggerInputs, Long actorUserId) {
        actorRunner.runAs(actorUserId, () -> executeInternal(
                def, executionId, triggerInputs, actorUserId, new WorkflowExecutionCheckpoint()));
    }

    @Async
    public void resumeAsync(WorkflowDefinitionModel def, Long executionId,
                            Map<String, Object> triggerInputs, Long actorUserId,
                            String checkpointJson) {
        WorkflowExecutionCheckpoint checkpoint;
        try {
            checkpoint = objectMapper.readValue(checkpointJson, WorkflowExecutionCheckpoint.class);
        } catch (Exception exception) {
            updateWorkflowFailed(executionId, "执行检查点损坏，无法恢复");
            sseNotifier.send(executionId, SseNotifier.workflowFailed("执行检查点损坏，无法恢复"));
            sseNotifier.complete(executionId);
            return;
        }
        actorRunner.runAs(actorUserId,
                () -> executeInternal(def, executionId, triggerInputs, actorUserId, checkpoint));
    }

    private void executeInternal(WorkflowDefinitionModel def, Long executionId,
                                 Map<String, Object> triggerInputs,
                                 Long actorUserId,
                                 WorkflowExecutionCheckpoint checkpoint) {
        long startTotal = System.currentTimeMillis();
        ExecutionContext ctx = new ExecutionContext(
                triggerInputs, resolveGlobalVariables(def), actorUserId);
        ctx.restoreNodeOutputs(checkpoint.getNodeOutputs());
        ctx.setSseNotifier(sseNotifier, executionId);
        runningContexts.put(executionId, ctx);

        List<WorkflowNodeModel> ordered;
        try {
            ordered = dagBuilder.topologicalSort(def.nodes(), def.edges());
        } catch (DAGBuilder.CyclicWorkflowException | DAGBuilder.InvalidWorkflowException e) {
            updateWorkflowFailed(executionId, e.getMessage());
            sseNotifier.send(executionId, SseNotifier.workflowFailed(e.getMessage()));
            sseNotifier.complete(executionId);
            runningContexts.remove(executionId);
            return;
        }

        Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> incomingEdges =
                dagBuilder.groupIncomingEdges(def.nodes(), def.edges());
        Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> outgoingEdges =
                dagBuilder.groupOutgoingEdges(def.nodes(), def.edges());
        Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates = restoreEdgeStates(def, checkpoint);
        Map<String, Object> endOutputs = new LinkedHashMap<>();
        for (WorkflowNodeModel node : ordered) {
            if ("end".equals(node.type()) && checkpoint.getCompletedNodeIds().contains(node.id())) {
                Map<String, Object> restored = checkpoint.getNodeOutputs().get(node.id());
                if (restored != null) endOutputs.putAll(restored);
            }
        }

        try {
            for (WorkflowNodeModel node : ordered) {
                if (ctx.isCancelled()) {
                    updateWorkflowCancelled(executionId);
                    sseNotifier.send(executionId, SseNotifier.workflowCancelled());
                    return;
                }

                if (checkpoint.getCompletedNodeIds().contains(node.id())
                        || checkpoint.getSkippedNodeIds().contains(node.id())) {
                    continue;
                }

                if (node.id().equals(checkpoint.getWaitingNodeId())) {
                    if ("timer".equals(checkpoint.getWaitingKind())) {
                        completeWaitingNode(executionId, node, ctx, edgeStates, outgoingEdges, checkpoint);
                        continue;
                    }
                    if ("approval".equals(checkpoint.getWaitingKind())
                            && !checkpoint.getWaitingResult().isEmpty()) {
                        completeWaitingNode(executionId, node, ctx, edgeStates, outgoingEdges, checkpoint);
                        continue;
                    }
                    if ("subworkflow".equals(checkpoint.getWaitingKind())
                            && !checkpoint.getWaitingResult().isEmpty()) {
                        completeWaitingNode(executionId, node, ctx, edgeStates, outgoingEdges, checkpoint);
                        continue;
                    }
                    if ("batch".equals(checkpoint.getWaitingKind())
                            && !checkpoint.getWaitingResult().isEmpty()) {
                        if (handleBatchResult(executionId, node, ctx, edgeStates,
                                outgoingEdges, checkpoint)) {
                            continue;
                        }
                        return;
                    }
                    if ("loop".equals(checkpoint.getWaitingKind())
                            && !checkpoint.getWaitingResult().isEmpty()) {
                        if (handleLoopResult(executionId, node, ctx, edgeStates,
                                outgoingEdges, checkpoint)) {
                            continue;
                        }
                        return;
                    }
                    if ("loop_interval".equals(checkpoint.getWaitingKind())) {
                        suspendForChild(executionId, checkpoint.getWaitingNodeExecutionId(), node,
                                childInputsFromMetadata(checkpoint), ctx, edgeStates, checkpoint, true);
                        return;
                    }
                    if ("retry".equals(checkpoint.getWaitingKind())) {
                        clearWaitingCheckpoint(checkpoint);
                    }
                }

                WorkflowExecutionPlanner.NodeDecision decision = executionPlanner.decide(
                        incomingEdges.getOrDefault(node.id(), List.of()), edgeStates);
                if (decision == WorkflowExecutionPlanner.NodeDecision.SKIPPED) {
                    saveNodeSkipped(executionId, node);
                    executionPlanner.skipNode(outgoingEdges.getOrDefault(node.id(), List.of()), edgeStates);
                    checkpoint.getSkippedNodeIds().add(node.id());
                    saveCheckpoint(executionId, ctx, edgeStates, checkpoint);
                    sseNotifier.send(executionId, SseNotifier.nodeSkipped(node.id()));
                    continue;
                }
                if (decision == WorkflowExecutionPlanner.NodeDecision.WAITING) {
                    throw new IllegalStateException("节点依赖尚未完成: " + node.id());
                }

                NodeExecutor executor = requireExecutor(node);
                Map<String, Object> resolvedInputs = ctx.resolveInputs(node.inputs());
                int attempt = checkpoint.getNodeAttempts().getOrDefault(node.id(), 0) + 1;
                checkpoint.getNodeAttempts().put(node.id(), attempt);
                Long nodeExecId = saveNodeStart(executionId, node, attempt);
                long nodeStart = System.currentTimeMillis();
                sseNotifier.send(executionId, SseNotifier.nodeRunning(node.id()));

                if ("delay".equals(node.type())) {
                    suspendForDelay(executionId, nodeExecId, node, resolvedInputs, ctx, edgeStates, checkpoint);
                    return;
                }
                if ("approval".equals(node.type())) {
                    suspendForApproval(executionId, nodeExecId, node, resolvedInputs,
                            ctx, edgeStates, checkpoint);
                    return;
                }
                if ("sub-workflow".equals(node.type()) || "loop".equals(node.type())) {
                    suspendForChild(executionId, nodeExecId, node, resolvedInputs,
                            ctx, edgeStates, checkpoint, "loop".equals(node.type()));
                    return;
                }
                if ("batch".equals(node.type())) {
                    boolean waitingForChild = suspendForBatch(executionId, nodeExecId, node, resolvedInputs,
                            ctx, edgeStates, outgoingEdges, checkpoint);
                    if (waitingForChild) return;
                    continue;
                }

                try {
                    Map<String, Object> outputs = executor.execute(resolvedInputs, node, ctx);
                    Map<String, Object> safeOutputs = outputs != null ? outputs : Map.of();
                    ctx.setNodeOutputs(node.id(), safeOutputs);
                    executionPlanner.completeNode(
                            outgoingEdges.getOrDefault(node.id(), List.of()), safeOutputs, edgeStates);
                    if ("end".equals(node.type())) {
                        endOutputs.putAll(safeOutputs);
                    }
                    checkpoint.getCompletedNodeIds().add(node.id());

                    long duration = System.currentTimeMillis() - nodeStart;
                    List<String> selectedPorts = selectedPorts(
                            outgoingEdges.getOrDefault(node.id(), List.of()), edgeStates);
                    saveNodeSuccess(nodeExecId, resolvedInputs, safeOutputs, selectedPorts, duration);
                    saveCheckpoint(executionId, ctx, edgeStates, checkpoint);
                    sseNotifier.send(executionId, SseNotifier.nodeSuccess(node.id(), safeOutputs, duration));
                } catch (NodeExecutionException exception) {
                    long duration = System.currentTimeMillis() - nodeStart;
                    if (ctx.isCancelled()) {
                        saveNodeCancelled(nodeExecId, resolvedInputs, duration);
                        updateWorkflowCancelled(executionId);
                        sseNotifier.send(executionId, SseNotifier.workflowCancelled());
                        return;
                    }
                    RetryPolicy retryPolicy = resolveRetryPolicy(node);
                    boolean shouldRetry = attempt < retryPolicy.maxAttempts();
                    saveNodeFailed(nodeExecId, resolvedInputs, exception.getMessage(), duration, shouldRetry);
                    sseNotifier.send(executionId,
                            SseNotifier.nodeFailed(node.id(), exception.getMessage(), duration));
                    if (shouldRetry) {
                        suspendForRetry(executionId, nodeExecId, node, attempt, retryPolicy,
                                ctx, edgeStates, checkpoint);
                        return;
                    }
                    if (exception.isFatal()) {
                        updateWorkflowFailed(executionId, exception.getMessage());
                        sseNotifier.send(executionId, SseNotifier.workflowFailed(exception.getMessage()));
                        return;
                    }
                    Map<String, Object> errorOutputs = Map.of(
                            "success", false,
                            "error", exception.getMessage() != null ? exception.getMessage() : "节点执行失败");
                    ctx.setNodeOutputs(node.id(), errorOutputs);
                    executionPlanner.completeNode(
                            outgoingEdges.getOrDefault(node.id(), List.of()), errorOutputs, edgeStates);
                    checkpoint.getCompletedNodeIds().add(node.id());
                    saveCheckpoint(executionId, ctx, edgeStates, checkpoint);
                }
            }

            long totalDuration = resolveTotalDuration(executionId, startTotal);
            Map<String, Object> finalOutputs = endOutputs.isEmpty()
                    ? ctx.collectFinalOutputs() : Map.copyOf(endOutputs);
            updateWorkflowSuccess(executionId, finalOutputs, totalDuration);
            sseNotifier.send(executionId, SseNotifier.workflowSuccess(finalOutputs, totalDuration));
        } catch (Exception exception) {
            log.error("Automation execution failed: executionId={}", executionId, exception);
            updateWorkflowFailed(executionId, exception.getMessage());
            sseNotifier.send(executionId, SseNotifier.workflowFailed(exception.getMessage()));
        } finally {
            runningContexts.remove(executionId);
            sseNotifier.complete(executionId);
        }
    }

    public boolean cancel(Long executionId) {
        ExecutionContext context = runningContexts.get(executionId);
        if (context == null) {
            return false;
        }
        context.cancel();
        return true;
    }

    public boolean isRunningLocally(Long executionId) {
        return runningContexts.containsKey(executionId);
    }

    @Scheduled(fixedDelay = 30000)
    public void heartbeatRunningExecutions() {
        LocalDateTime now = LocalDateTime.now();
        runningContexts.keySet().forEach(executionId -> {
            AutomationExecution heartbeat = new AutomationExecution();
            heartbeat.setId(executionId);
            heartbeat.setHeartbeatAt(now);
            executionMapper.updateById(heartbeat);
        });
    }

    private NodeExecutor requireExecutor(WorkflowNodeModel node) throws NodeExecutionException {
        NodeExecutor executor = nodeRegistry.getExecutor(node.type());
        if (executor == null) {
            throw new NodeExecutionException(node.id(), "不支持的节点类型: " + node.type());
        }
        return executor;
    }

    private Map<String, Object> resolveGlobalVariables(WorkflowDefinitionModel definition) {
        if (definition.globalVariables() == null || definition.globalVariables().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        definition.globalVariables().forEach((name, variable) ->
                values.put(name, variable != null ? variable.defaultValue() : null));
        return values;
    }

    private List<String> selectedPorts(
            List<com.trackflow.automation.node.model.WorkflowEdgeModel> edges,
            Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates) {
        List<String> ports = new ArrayList<>();
        for (com.trackflow.automation.node.model.WorkflowEdgeModel edge : edges) {
            if (edgeStates.get(edge.id()) == WorkflowExecutionPlanner.EdgeState.SUCCESS
                    && !"__flow".equals(edge.sourcePortName())
                    && !ports.contains(edge.sourcePortName())) {
                ports.add(edge.sourcePortName());
            }
        }
        return ports;
    }

    private Long saveNodeStart(Long execId, WorkflowNodeModel node, int attempt) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setExecutionId(execId);
        r.setNodeId(node.id());
        r.setNodeType(node.type());
        r.setNodeName(node.nodeMeta() != null ? node.nodeMeta().title() : node.type());
        r.setStatus(AutomationNodeStatus.RUNNING.getValue());
        r.setAttempt(attempt);
        r.setStartedAt(LocalDateTime.now());
        nodeExecutionMapper.insert(r);
        return r.getId();
    }

    private void saveNodeSuccess(Long id, Map<String,Object> in, Map<String,Object> out,
                                 List<String> selectedPorts, long dur) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setId(id);
        r.setStatus(AutomationNodeStatus.SUCCESS.getValue());
        r.setOutcome("success");
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        try {
            r.setInput(objectMapper.writeValueAsString(in));
            r.setOutput(objectMapper.writeValueAsString(out));
            r.setSelectedPorts(objectMapper.writeValueAsString(selectedPorts));
        } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(r);
    }

    private void saveNodeFailed(Long id, Map<String,Object> in, String error, long dur,
                                boolean retryable) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setId(id);
        r.setStatus(AutomationNodeStatus.FAILED.getValue());
        r.setOutcome("failed");
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        r.setErrorInfo(error);
        r.setRetryable(retryable);
        try { r.setInput(objectMapper.writeValueAsString(in)); } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(r);
    }

    private void saveNodeCancelled(Long id, Map<String, Object> input, long duration) {
        AutomationNodeExecution record = new AutomationNodeExecution();
        record.setId(id);
        record.setStatus(AutomationNodeStatus.CANCELLED.getValue());
        record.setOutcome("cancelled");
        record.setFinishedAt(LocalDateTime.now());
        record.setDurationMs(duration);
        try { record.setInput(objectMapper.writeValueAsString(input)); } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(record);
    }

    private void saveNodeSkipped(Long executionId, WorkflowNodeModel node) {
        AutomationNodeExecution record = new AutomationNodeExecution();
        record.setExecutionId(executionId);
        record.setNodeId(node.id());
        record.setNodeType(node.type());
        record.setNodeName(node.nodeMeta() != null ? node.nodeMeta().title() : node.type());
        record.setStatus(AutomationNodeStatus.SKIPPED.getValue());
        record.setOutcome("branch_skipped");
        record.setAttempt(1);
        record.setStartedAt(LocalDateTime.now());
        record.setFinishedAt(LocalDateTime.now());
        record.setDurationMs(0L);
        nodeExecutionMapper.insert(record);
    }

    private void saveCheckpoint(Long executionId, ExecutionContext context,
                                Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                WorkflowExecutionCheckpoint checkpoint) {
        AutomationExecution record = new AutomationExecution();
        record.setId(executionId);
        record.setHeartbeatAt(LocalDateTime.now());
        try {
            checkpoint.setNodeOutputs(context.snapshotNodeOutputs());
            Map<String, String> serializedEdgeStates = new LinkedHashMap<>();
            edgeStates.forEach((edgeId, state) -> serializedEdgeStates.put(edgeId, state.name()));
            checkpoint.setEdgeStates(serializedEdgeStates);
            record.setCheckpoint(objectMapper.writeValueAsString(checkpoint));
        } catch (Exception exception) {
            log.warn("Failed to serialize automation checkpoint: executionId={}", executionId, exception);
        }
        executionMapper.updateById(record);
    }

    private Map<String, WorkflowExecutionPlanner.EdgeState> restoreEdgeStates(
            WorkflowDefinitionModel definition, WorkflowExecutionCheckpoint checkpoint) {
        Map<String, WorkflowExecutionPlanner.EdgeState> states =
                executionPlanner.initializeEdges(definition.edges());
        checkpoint.getEdgeStates().forEach((edgeId, value) -> {
            if (!states.containsKey(edgeId)) return;
            try {
                states.put(edgeId, WorkflowExecutionPlanner.EdgeState.valueOf(value));
            } catch (IllegalArgumentException exception) {
                throw new DAGBuilder.InvalidWorkflowException("执行检查点包含未知边状态: " + value);
            }
        });
        return states;
    }

    private void suspendForDelay(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                 Map<String, Object> inputs, ExecutionContext context,
                                 Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                 WorkflowExecutionCheckpoint checkpoint) {
        int seconds = resolveDelaySeconds(node, inputs);
        LocalDateTime wakeUpAt = LocalDateTime.now().plusSeconds(seconds);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(nodeExecutionId);
        nodeRecord.setStatus(AutomationNodeStatus.WAITING.getValue());
        nodeRecord.setOutcome("waiting_timer");
        try {
            nodeRecord.setInput(objectMapper.writeValueAsString(inputs));
        } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(nodeRecord);

        checkpoint.setWaitingNodeId(node.id());
        checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
        checkpoint.setWaitingKind("timer");
        checkpoint.setWaitingUntil(wakeUpAt);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.WAITING_TIMER.getValue());
        execution.setWakeUpAt(wakeUpAt);
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        sseNotifier.send(executionId, SseNotifier.workflowWaitingTimer(node.id(), wakeUpAt));
    }

    private void suspendForRetry(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                 int attempt, RetryPolicy policy, ExecutionContext context,
                                 Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                 WorkflowExecutionCheckpoint checkpoint) {
        long delaySeconds = policy.delaySecondsForAttempt(attempt);
        LocalDateTime wakeUpAt = LocalDateTime.now().plusSeconds(delaySeconds);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(nodeExecutionId);
        nodeRecord.setOutcome("retry_scheduled");
        nodeRecord.setRetryable(true);
        nodeExecutionMapper.updateById(nodeRecord);

        checkpoint.setWaitingNodeId(node.id());
        checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
        checkpoint.setWaitingKind("retry");
        checkpoint.setWaitingUntil(wakeUpAt);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.RETRYING.getValue());
        execution.setWakeUpAt(wakeUpAt);
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    private void suspendForApproval(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                    Map<String, Object> inputs, ExecutionContext context,
                                    Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                    WorkflowExecutionCheckpoint checkpoint) {
        AutomationApproval approval = new AutomationApproval();
        approval.setExecutionId(executionId);
        approval.setNodeExecutionId(nodeExecutionId);
        approval.setNodeId(node.id());
        approval.setTitle(String.valueOf(inputs.getOrDefault("title", "自动化操作审批")));
        approval.setDescription(inputs.get("description") != null
                ? inputs.get("description").toString() : null);
        approval.setRiskLevel(node.config() != null
                ? String.valueOf(node.config().getOrDefault("riskLevel", "medium")) : "medium");
        approval.setStatus("pending");
        approval.setRequestedBy(context.getActorUserId());
        approval.setCreatedAt(LocalDateTime.now());
        int expiryHours = node.config() != null
                ? intValue(node.config().get("expiryHours"), 24, 1, 720) : 24;
        approval.setExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        try {
            approval.setRequestPayload(objectMapper.writeValueAsString(
                    inputs.getOrDefault("payload", Map.of())));
        } catch (Exception exception) {
            throw new DAGBuilder.InvalidWorkflowException("审批数据无法序列化: " + node.id());
        }
        approvalMapper.insert(approval);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(nodeExecutionId);
        nodeRecord.setStatus(AutomationNodeStatus.WAITING.getValue());
        nodeRecord.setOutcome("waiting_approval");
        nodeExecutionMapper.updateById(nodeRecord);

        checkpoint.setWaitingNodeId(node.id());
        checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
        checkpoint.setWaitingKind("approval");
        checkpoint.setWaitingResult(Map.of());
        checkpoint.setWaitingUntil(null);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.WAITING_APPROVAL.getValue());
        execution.setWakeUpAt(null);
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        sseNotifier.send(executionId,
                SseNotifier.workflowWaitingApproval(node.id(), approval.getId()));
    }

    private void suspendForChild(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                 Map<String, Object> inputs, ExecutionContext context,
                                 Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                 WorkflowExecutionCheckpoint checkpoint, boolean loop) {
        Object workflowValue = node.config() != null ? node.config().get("workflowId") : null;
        if (workflowValue == null || workflowValue.toString().isBlank()) {
            throw new DAGBuilder.InvalidWorkflowException("节点未配置子工作流: " + node.id());
        }
        Long childWorkflowId;
        try {
            childWorkflowId = workflowValue instanceof Number number
                    ? number.longValue() : Long.valueOf(workflowValue.toString());
        } catch (NumberFormatException exception) {
            throw new DAGBuilder.InvalidWorkflowException("子工作流 ID 无效: " + node.id());
        }
        Map<String, Object> childInputs = normalizeChildInputs(inputs);
        int iteration = loop
                ? intValue(checkpoint.getWaitingMetadata().get("iteration"), 1, 1, 20) : 1;
        Long childExecutionId = executionServiceProvider.getObject().startChild(
                childWorkflowId, childInputs, executionId, node.id());

        Map<String, Object> metadata = new LinkedHashMap<>(checkpoint.getWaitingMetadata());
        metadata.put("childExecutionId", childExecutionId);
        metadata.put("childWorkflowId", childWorkflowId);
        metadata.put("childInput", childInputs);
        metadata.put("iteration", iteration);
        checkpoint.setWaitingMetadata(metadata);
        checkpoint.setWaitingNodeId(node.id());
        checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
        checkpoint.setWaitingKind(loop ? "loop" : "subworkflow");
        checkpoint.setWaitingResult(Map.of());
        checkpoint.setWaitingUntil(null);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(nodeExecutionId);
        nodeRecord.setStatus(AutomationNodeStatus.WAITING.getValue());
        nodeRecord.setOutcome("waiting_child_workflow");
        nodeExecutionMapper.updateById(nodeRecord);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.WAITING_EVENT.getValue());
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    private boolean handleLoopResult(
            Long executionId, WorkflowNodeModel node, ExecutionContext context,
            Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
            Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> outgoingEdges,
            WorkflowExecutionCheckpoint checkpoint) {
        int iteration = intValue(checkpoint.getWaitingMetadata().get("iteration"), 1, 1, 20);
        int maxRetries = node.config() != null
                ? intValue(node.config().get("maxRetries"), 3, 1, 20) : 3;
        Object childOutput = checkpoint.getWaitingResult().get("output");
        boolean conditionMet = evaluateLoopExit(node, childOutput,
                checkpoint.getWaitingResult().get("status"));
        boolean exhausted = !conditionMet && iteration >= maxRetries;
        if (conditionMet || exhausted) {
            Map<String, Object> finalResult = new LinkedHashMap<>();
            finalResult.put("output", childOutput);
            finalResult.put("iteration", iteration);
            finalResult.put("success", conditionMet);
            finalResult.put("exhausted", exhausted);
            checkpoint.setWaitingResult(finalResult);
            completeWaitingNode(executionId, node, context, edgeStates, outgoingEdges, checkpoint);
            return true;
        }

        int interval = node.config() != null
                ? intValue(node.config().get("interval"), 5, 0, 300) : 5;
        Map<String, Object> metadata = new LinkedHashMap<>(checkpoint.getWaitingMetadata());
        metadata.put("iteration", iteration + 1);
        checkpoint.setWaitingMetadata(metadata);
        checkpoint.setWaitingKind("loop_interval");
        checkpoint.setWaitingResult(Map.of());
        checkpoint.setWaitingUntil(LocalDateTime.now().plusSeconds(interval));
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.RETRYING.getValue());
        execution.setWakeUpAt(checkpoint.getWaitingUntil());
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean suspendForBatch(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                    Map<String, Object> inputs, ExecutionContext context,
                                    Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                    Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> outgoingEdges,
                                    WorkflowExecutionCheckpoint checkpoint) {
        Object rawItems = inputs.get("items");
        if (!(rawItems instanceof List<?> rawList)) {
            throw new DAGBuilder.InvalidWorkflowException("批处理节点的待处理列表必须是数组: " + node.id());
        }
        int maxItems = node.config() != null
                ? intValue(node.config().get("maxItems"), 100, 1, 1000) : 100;
        List<Object> items = new ArrayList<>();
        List<Object> skippedItems = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            if (index < maxItems) items.add(rawList.get(index));
            else skippedItems.add(rawList.get(index));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("items", items);
        metadata.put("index", 0);
        metadata.put("successItems", new ArrayList<>());
        metadata.put("failedItems", new ArrayList<>());
        metadata.put("skippedItems", skippedItems);
        checkpoint.setWaitingMetadata(metadata);
        if (items.isEmpty()) {
            checkpoint.setWaitingNodeId(node.id());
            checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
            checkpoint.setWaitingKind("batch");
            finishBatch(executionId, node, context, edgeStates, checkpoint);
            completeWaitingNode(executionId, node, context, edgeStates, outgoingEdges, checkpoint);
            return false;
        }
        suspendNextBatchChild(executionId, nodeExecutionId, node, context, edgeStates, checkpoint);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void suspendNextBatchChild(Long executionId, Long nodeExecutionId, WorkflowNodeModel node,
                                       ExecutionContext context,
                                       Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                                       WorkflowExecutionCheckpoint checkpoint) {
        Map<String, Object> metadata = new LinkedHashMap<>(checkpoint.getWaitingMetadata());
        List<Object> items = metadata.get("items") instanceof List<?> list
                ? (List<Object>) list : List.of();
        int index = intValue(metadata.get("index"), 0, 0, Math.max(0, items.size()));
        if (index >= items.size()) {
            finishBatch(executionId, node, context, edgeStates, checkpoint);
            return;
        }
        Long childWorkflowId = childWorkflowId(node);
        String itemInputKey = node.config() != null
                ? String.valueOf(node.config().getOrDefault("itemInputKey", "item")) : "item";
        if (itemInputKey.isBlank()) itemInputKey = "item";
        Map<String, Object> childInput = new LinkedHashMap<>();
        childInput.put(itemInputKey, items.get(index));
        childInput.put("index", index);
        Long childExecutionId = executionServiceProvider.getObject().startChild(
                childWorkflowId, childInput, executionId, node.id());

        metadata.put("childExecutionId", childExecutionId);
        metadata.put("childWorkflowId", childWorkflowId);
        metadata.put("childInput", childInput);
        checkpoint.setWaitingMetadata(metadata);
        checkpoint.setWaitingNodeId(node.id());
        checkpoint.setWaitingNodeExecutionId(nodeExecutionId);
        checkpoint.setWaitingKind("batch");
        checkpoint.setWaitingResult(Map.of());
        checkpoint.setWaitingUntil(null);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(nodeExecutionId);
        nodeRecord.setStatus(AutomationNodeStatus.WAITING.getValue());
        nodeRecord.setOutcome("waiting_batch_item");
        nodeExecutionMapper.updateById(nodeRecord);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);

        AutomationExecution execution = new AutomationExecution();
        execution.setId(executionId);
        execution.setStatus(AutomationExecutionStatus.WAITING_EVENT.getValue());
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
    }

    @SuppressWarnings("unchecked")
    private boolean handleBatchResult(
            Long executionId, WorkflowNodeModel node, ExecutionContext context,
            Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
            Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> outgoingEdges,
            WorkflowExecutionCheckpoint checkpoint) {
        Map<String, Object> metadata = new LinkedHashMap<>(checkpoint.getWaitingMetadata());
        List<Object> items = metadata.get("items") instanceof List<?> list ? (List<Object>) list : List.of();
        int index = intValue(metadata.get("index"), 0, 0, Math.max(0, items.size()));
        Object item = index < items.size() ? items.get(index) : null;
        boolean success = Boolean.TRUE.equals(checkpoint.getWaitingResult().get("success"));
        List<Map<String, Object>> successItems = mutableResultItems(metadata.get("successItems"));
        List<Map<String, Object>> failedItems = mutableResultItems(metadata.get("failedItems"));
        Map<String, Object> itemResult = new LinkedHashMap<>();
        itemResult.put("index", index);
        itemResult.put("item", item);
        itemResult.put("output", checkpoint.getWaitingResult().getOrDefault("output", Map.of()));
        if (success) successItems.add(itemResult);
        else {
            itemResult.put("error", checkpoint.getWaitingResult().getOrDefault("error", "子工作流执行失败"));
            failedItems.add(itemResult);
        }
        metadata.put("successItems", successItems);
        metadata.put("failedItems", failedItems);
        metadata.put("index", index + 1);
        checkpoint.setWaitingMetadata(metadata);

        String failureMode = node.config() != null
                ? String.valueOf(node.config().getOrDefault("onItemFailure", "continue")) : "continue";
        if (!success && "stop".equals(failureMode)) {
            List<Object> skippedItems = mutableItems(metadata.get("skippedItems"));
            for (int remaining = index + 1; remaining < items.size(); remaining++) {
                skippedItems.add(items.get(remaining));
            }
            metadata.put("skippedItems", skippedItems);
            checkpoint.setWaitingMetadata(metadata);
            finishBatch(executionId, node, context, edgeStates, checkpoint);
            completeWaitingNode(executionId, node, context, edgeStates, outgoingEdges, checkpoint);
            return true;
        }
        if (index + 1 >= items.size()) {
            finishBatch(executionId, node, context, edgeStates, checkpoint);
            completeWaitingNode(executionId, node, context, edgeStates, outgoingEdges, checkpoint);
            return true;
        }
        suspendNextBatchChild(executionId, checkpoint.getWaitingNodeExecutionId(), node,
                context, edgeStates, checkpoint);
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mutableResultItems(Object value) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) results.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<Object> mutableItems(Object value) {
        return value instanceof List<?> list ? new ArrayList<>((List<Object>) list) : new ArrayList<>();
    }

    private void finishBatch(Long executionId, WorkflowNodeModel node, ExecutionContext context,
                             Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
                             WorkflowExecutionCheckpoint checkpoint) {
        Map<String, Object> metadata = checkpoint.getWaitingMetadata();
        List<?> items = metadata.get("items") instanceof List<?> list ? list : List.of();
        List<?> succeeded = metadata.get("successItems") instanceof List<?> list ? list : List.of();
        List<?> failed = metadata.get("failedItems") instanceof List<?> list ? list : List.of();
        List<?> skipped = metadata.get("skippedItems") instanceof List<?> list ? list : List.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size() + skipped.size());
        summary.put("processed", succeeded.size() + failed.size());
        summary.put("succeeded", succeeded.size());
        summary.put("failed", failed.size());
        summary.put("skipped", skipped.size());
        checkpoint.setWaitingResult(Map.of(
                "successItems", succeeded,
                "failedItems", failed,
                "skippedItems", skipped,
                "summary", summary));
        saveCheckpoint(executionId, context, edgeStates, checkpoint);
    }

    private Long childWorkflowId(WorkflowNodeModel node) {
        Object workflowValue = node.config() != null ? node.config().get("workflowId") : null;
        if (workflowValue == null || workflowValue.toString().isBlank()) {
            throw new DAGBuilder.InvalidWorkflowException("批处理节点未配置子工作流: " + node.id());
        }
        try {
            return workflowValue instanceof Number number
                    ? number.longValue() : Long.valueOf(workflowValue.toString());
        } catch (NumberFormatException exception) {
            throw new DAGBuilder.InvalidWorkflowException("批处理子工作流 ID 无效: " + node.id());
        }
    }

    private boolean evaluateLoopExit(WorkflowNodeModel node, Object output, Object status) {
        String operator = node.config() != null
                ? String.valueOf(node.config().getOrDefault("exitOperator", "contains")) : "contains";
        String expected = node.config() != null
                ? String.valueOf(node.config().getOrDefault("exitValue", "PASS")) : "PASS";
        String actual;
        try {
            actual = output instanceof String value ? value : objectMapper.writeValueAsString(output);
        } catch (Exception exception) {
            actual = String.valueOf(output);
        }
        return switch (operator) {
            case "not_contains" -> !actual.contains(expected);
            case "equals" -> actual.equals(expected);
            case "not_equals" -> !actual.equals(expected);
            case "is_empty" -> actual.isBlank() || "null".equals(actual) || "{}".equals(actual);
            case "is_not_empty" -> !(actual.isBlank() || "null".equals(actual) || "{}".equals(actual));
            case "exit_zero" -> "success".equals(String.valueOf(status));
            default -> actual.contains(expected);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeChildInputs(Map<String, Object> inputs) {
        Object nested = inputs.get("input");
        if (nested instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return new LinkedHashMap<>(inputs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childInputsFromMetadata(WorkflowExecutionCheckpoint checkpoint) {
        Object input = checkpoint.getWaitingMetadata().get("childInput");
        return input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private RetryPolicy resolveRetryPolicy(WorkflowNodeModel node) {
        var contract = nodeRegistry.getContract(node.type());
        int defaultMaxAttempts = contract == null ? 1 : contract.runtime().defaultMaxAttempts();
        if (contract != null && !contract.runtime().retrySafe()) {
            return RetryPolicy.NONE;
        }
        if (node.config() == null) return new RetryPolicy(defaultMaxAttempts, 0, 1.0, 0);
        Object rawPolicy = node.config().get("retryPolicy");
        if (!(rawPolicy instanceof Map<?, ?> policy)) return new RetryPolicy(defaultMaxAttempts, 0, 1.0, 0);
        int maxAttempts = intValue(policy.get("maxAttempts"), defaultMaxAttempts, 1, 10);
        int initialDelay = intValue(policy.get("initialDelaySeconds"), 2, 0, 3600);
        double multiplier = doubleValue(policy.get("backoffMultiplier"), 2.0, 1.0, 10.0);
        int maxDelay = intValue(policy.get("maxDelaySeconds"), 300, 0, 86400);
        return new RetryPolicy(maxAttempts, initialDelay, multiplier, maxDelay);
    }

    private int intValue(Object value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            int parsed = value instanceof Number number ? number.intValue()
                    : Integer.parseInt(value.toString());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double doubleValue(Object value, double fallback, double min, double max) {
        if (value == null) return fallback;
        try {
            double parsed = value instanceof Number number ? number.doubleValue()
                    : Double.parseDouble(value.toString());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record RetryPolicy(int maxAttempts, int initialDelaySeconds,
                               double backoffMultiplier, int maxDelaySeconds) {
        private static final RetryPolicy NONE = new RetryPolicy(1, 0, 1.0, 0);

        long delaySecondsForAttempt(int completedAttempt) {
            double computed = initialDelaySeconds
                    * Math.pow(backoffMultiplier, Math.max(0, completedAttempt - 1));
            return Math.min(maxDelaySeconds, Math.round(computed));
        }
    }

    private int resolveDelaySeconds(WorkflowNodeModel node, Map<String, Object> inputs) {
        Object configuredValue = inputs.get("duration");
        if (configuredValue == null && node.config() != null) {
            configuredValue = node.config().get("seconds");
        }
        if (configuredValue == null) return 5;
        try {
            int seconds = configuredValue instanceof Number number
                    ? number.intValue() : Integer.parseInt(configuredValue.toString());
            return Math.max(0, Math.min(seconds, 2_592_000));
        } catch (NumberFormatException exception) {
            throw new DAGBuilder.InvalidWorkflowException("延时节点时长必须是整数: " + node.id());
        }
    }

    private void completeWaitingNode(
            Long executionId, WorkflowNodeModel node, ExecutionContext context,
            Map<String, WorkflowExecutionPlanner.EdgeState> edgeStates,
            Map<String, List<com.trackflow.automation.node.model.WorkflowEdgeModel>> outgoingEdges,
            WorkflowExecutionCheckpoint checkpoint) {
        Map<String, Object> outputs = checkpoint.getWaitingResult().isEmpty()
                ? Map.of("done", true) : Map.copyOf(checkpoint.getWaitingResult());
        context.setNodeOutputs(node.id(), outputs);
        executionPlanner.completeNode(outgoingEdges.getOrDefault(node.id(), List.of()), outputs, edgeStates);

        AutomationNodeExecution nodeRecord = new AutomationNodeExecution();
        nodeRecord.setId(checkpoint.getWaitingNodeExecutionId());
        nodeRecord.setStatus(AutomationNodeStatus.SUCCESS.getValue());
        String waitingKind = checkpoint.getWaitingKind();
        nodeRecord.setOutcome(switch (waitingKind != null ? waitingKind : "") {
            case "approval" -> Boolean.TRUE.equals(outputs.get("approved")) ? "approved" : "rejected";
            case "subworkflow" -> "child_" + outputs.getOrDefault("status", "completed");
            case "loop" -> Boolean.TRUE.equals(outputs.get("success")) ? "loop_satisfied" : "loop_exhausted";
            case "batch" -> "batch_completed";
            default -> "timer_elapsed";
        });
        nodeRecord.setFinishedAt(LocalDateTime.now());
        try {
            nodeRecord.setOutput(objectMapper.writeValueAsString(outputs));
            nodeRecord.setSelectedPorts(objectMapper.writeValueAsString(
                    selectedPorts(outgoingEdges.getOrDefault(node.id(), List.of()), edgeStates)));
        } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(nodeRecord);

        checkpoint.getCompletedNodeIds().add(node.id());
        clearWaitingCheckpoint(checkpoint);
        saveCheckpoint(executionId, context, edgeStates, checkpoint);
        sseNotifier.send(executionId, SseNotifier.nodeSuccess(node.id(), outputs, 0L));
    }

    private void clearWaitingCheckpoint(WorkflowExecutionCheckpoint checkpoint) {
        checkpoint.setWaitingNodeId(null);
        checkpoint.setWaitingNodeExecutionId(null);
        checkpoint.setWaitingKind(null);
        checkpoint.setWaitingResult(Map.of());
        checkpoint.setWaitingMetadata(Map.of());
        checkpoint.setWaitingUntil(null);
    }

    private void updateWorkflowFailed(Long id, String error) {
        AutomationExecution r = new AutomationExecution();
        r.setId(id);
        r.setStatus(AutomationExecutionStatus.FAILED.getValue());
        r.setErrorMessage(error);
        r.setFinishedAt(LocalDateTime.now());
        executionMapper.updateById(r);
    }

    private void updateWorkflowSuccess(Long id, Map<String,Object> out, long dur) {
        AutomationExecution r = new AutomationExecution();
        r.setId(id);
        r.setStatus(AutomationExecutionStatus.SUCCESS.getValue());
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        try { r.setOutput(objectMapper.writeValueAsString(out)); } catch (Exception ignored) {}
        executionMapper.updateById(r);
    }

    private void updateWorkflowCancelled(Long id) {
        AutomationExecution record = new AutomationExecution();
        record.setId(id);
        record.setStatus(AutomationExecutionStatus.CANCELLED.getValue());
        record.setFinishedAt(LocalDateTime.now());
        executionMapper.updateById(record);
    }

    private long resolveTotalDuration(Long executionId, long segmentStartMillis) {
        AutomationExecution execution = executionMapper.selectById(executionId);
        if (execution != null && execution.getStartedAt() != null) {
            return java.time.Duration.between(execution.getStartedAt(), LocalDateTime.now()).toMillis();
        }
        return System.currentTimeMillis() - segmentStartMillis;
    }
}

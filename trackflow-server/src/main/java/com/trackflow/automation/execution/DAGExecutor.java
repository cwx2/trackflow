package com.trackflow.automation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.entity.AutomationExecution;
import com.trackflow.automation.execution.entity.AutomationNodeExecution;
import com.trackflow.automation.execution.mapper.AutomationExecutionMapper;
import com.trackflow.automation.execution.mapper.AutomationNodeExecutionMapper;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeRegistry;
import com.trackflow.automation.node.model.WorkflowDefinitionModel;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DAGExecutor {

    private static final Logger log = LoggerFactory.getLogger(DAGExecutor.class);

    private final NodeRegistry nodeRegistry;
    private final DAGBuilder dagBuilder;
    private final SseNotifier sseNotifier;
    private final AutomationExecutionMapper executionMapper;
    private final AutomationNodeExecutionMapper nodeExecutionMapper;
    private final ObjectMapper objectMapper;

    public DAGExecutor(NodeRegistry nodeRegistry, DAGBuilder dagBuilder, SseNotifier sseNotifier,
                       AutomationExecutionMapper executionMapper,
                       AutomationNodeExecutionMapper nodeExecutionMapper,
                       ObjectMapper objectMapper) {
        this.nodeRegistry = nodeRegistry;
        this.dagBuilder = dagBuilder;
        this.sseNotifier = sseNotifier;
        this.executionMapper = executionMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.objectMapper = objectMapper;
    }

    @Async
    public void executeAsync(WorkflowDefinitionModel def, Long executionId, Map<String, Object> triggerInputs) {
        long startTotal = System.currentTimeMillis();
        ExecutionContext ctx = new ExecutionContext(triggerInputs, Map.of());
        ctx.setSseNotifier(sseNotifier, executionId);

        List<WorkflowNodeModel> ordered;
        try {
            ordered = dagBuilder.topologicalSort(def.nodes(), def.edges());
        } catch (DAGBuilder.CyclicWorkflowException e) {
            updateWorkflowFailed(executionId, e.getMessage());
            sseNotifier.send(executionId, SseNotifier.workflowFailed(e.getMessage()));
            sseNotifier.complete(executionId);
            return;
        }

        Map<String, Object> finalOutputs = Map.of();

        for (WorkflowNodeModel node : ordered) {
            if (ctx.isCancelled()) break;
            NodeExecutor executor = nodeRegistry.getExecutor(node.type());
            if (executor == null) { log.warn("No executor for node type: {}", node.type()); continue; }

            Map<String, Object> resolvedInputs = ctx.resolveInputs(node.inputs());
            Long nodeExecId = saveNodeStart(executionId, node);
            long nodeStart  = System.currentTimeMillis();
            sseNotifier.send(executionId, SseNotifier.nodeRunning(node.id()));

            try {
                Map<String, Object> outputs = executor.execute(resolvedInputs, node, ctx);
                ctx.setNodeOutputs(node.id(), outputs);
                finalOutputs = outputs;
                long dur = System.currentTimeMillis() - nodeStart;
                saveNodeSuccess(nodeExecId, resolvedInputs, outputs, dur);
                sseNotifier.send(executionId, SseNotifier.nodeSuccess(node.id(), outputs, dur));
            } catch (NodeExecutionException e) {
                long dur = System.currentTimeMillis() - nodeStart;
                saveNodeFailed(nodeExecId, resolvedInputs, e.getMessage(), dur);
                sseNotifier.send(executionId, SseNotifier.nodeFailed(node.id(), e.getMessage(), dur));
                if (e.isFatal()) {
                    updateWorkflowFailed(executionId, e.getMessage());
                    sseNotifier.send(executionId, SseNotifier.workflowFailed(e.getMessage()));
                    sseNotifier.complete(executionId);
                    return;
                }
            } catch (Exception e) {
                long dur = System.currentTimeMillis() - nodeStart;
                saveNodeFailed(nodeExecId, resolvedInputs, e.getMessage(), dur);
                sseNotifier.send(executionId, SseNotifier.nodeFailed(node.id(), e.getMessage(), dur));
                updateWorkflowFailed(executionId, e.getMessage());
                sseNotifier.send(executionId, SseNotifier.workflowFailed(e.getMessage()));
                sseNotifier.complete(executionId);
                return;
            }
        }

        long totalDur = System.currentTimeMillis() - startTotal;
        updateWorkflowSuccess(executionId, finalOutputs, totalDur);
        sseNotifier.send(executionId, SseNotifier.workflowSuccess(finalOutputs, totalDur));
        sseNotifier.complete(executionId);
    }

    private Long saveNodeStart(Long execId, WorkflowNodeModel node) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setExecutionId(execId);
        r.setNodeId(node.id());
        r.setNodeType(node.type());
        r.setNodeName(node.nodeMeta() != null ? node.nodeMeta().title() : node.type());
        r.setStatus("running");
        r.setStartedAt(LocalDateTime.now());
        nodeExecutionMapper.insert(r);
        return r.getId();
    }

    private void saveNodeSuccess(Long id, Map<String,Object> in, Map<String,Object> out, long dur) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setId(id);
        r.setStatus("success");
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        try {
            r.setInput(objectMapper.writeValueAsString(in));
            r.setOutput(objectMapper.writeValueAsString(out));
        } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(r);
    }

    private void saveNodeFailed(Long id, Map<String,Object> in, String error, long dur) {
        AutomationNodeExecution r = new AutomationNodeExecution();
        r.setId(id);
        r.setStatus("failed");
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        r.setErrorInfo(error);
        try { r.setInput(objectMapper.writeValueAsString(in)); } catch (Exception ignored) {}
        nodeExecutionMapper.updateById(r);
    }

    private void updateWorkflowFailed(Long id, String error) {
        AutomationExecution r = new AutomationExecution();
        r.setId(id);
        r.setStatus("failed");
        r.setErrorMessage(error);
        r.setFinishedAt(LocalDateTime.now());
        executionMapper.updateById(r);
    }

    private void updateWorkflowSuccess(Long id, Map<String,Object> out, long dur) {
        AutomationExecution r = new AutomationExecution();
        r.setId(id);
        r.setStatus("success");
        r.setFinishedAt(LocalDateTime.now());
        r.setDurationMs(dur);
        try { r.setOutput(objectMapper.writeValueAsString(out)); } catch (Exception ignored) {}
        executionMapper.updateById(r);
    }
}

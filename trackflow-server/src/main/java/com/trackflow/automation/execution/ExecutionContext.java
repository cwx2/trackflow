package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.InputParameter;
import com.trackflow.automation.node.model.LiteralValue;
import com.trackflow.automation.node.model.VariableRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecutionContext {
    private final Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVars;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private SseNotifier sseNotifier;
    private Long executionId;

    public ExecutionContext(Map<String, Object> triggerInputs, Map<String, Object> globalVars) {
        this.globalVars = globalVars != null ? globalVars : new HashMap<>();
        if (triggerInputs != null) nodeOutputs.put("__trigger__", triggerInputs);
    }

    public void setNodeOutputs(String nodeId, Map<String, Object> outputs) {
        nodeOutputs.put(nodeId, outputs != null ? outputs : new HashMap<>());
    }

    public Object getNodeOutput(String nodeId, String portName) {
        Map<String, Object> outputs = nodeOutputs.get(nodeId);
        return outputs == null ? null : outputs.get(portName);
    }

    public Map<String, Object> resolveInputs(List<InputParameter> inputs) {
        Map<String, Object> resolved = new HashMap<>();
        if (inputs == null) return resolved;
        for (InputParameter input : inputs) {
            Object value = null;
            if (input.value() instanceof VariableRef ref) {
                value = getNodeOutput(ref.nodeId(), ref.outputName());
            } else if (input.value() instanceof LiteralValue lit) {
                value = lit.value();
            }
            resolved.put(input.name(), value);
        }
        return resolved;
    }

    public Map<String, Object> collectFinalOutputs() {
        Map<String, Object> result = new HashMap<>();
        nodeOutputs.forEach((nodeId, outputs) -> {
            if (!"__trigger__".equals(nodeId))
                outputs.forEach((k, v) -> result.put(nodeId + "." + k, v));
        });
        return result;
    }

    public boolean isCancelled() { return cancelled.get(); }
    public void cancel()          { cancelled.set(true); }

    public void setSseNotifier(SseNotifier notifier, Long executionId) {
        this.sseNotifier = notifier; this.executionId = executionId;
    }

    public void sendSseEvent(Object event) {
        if (sseNotifier != null && executionId != null) sseNotifier.send(executionId, event);
    }

    public Map<String, Object> getGlobalVars() { return globalVars; }
}

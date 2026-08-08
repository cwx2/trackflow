package com.trackflow.automation.execution;

import com.trackflow.automation.node.model.InputParameter;
import com.trackflow.automation.node.model.LiteralValue;
import com.trackflow.automation.node.model.TemplateValue;
import com.trackflow.automation.node.model.VariableRef;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutionContext {
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{([^.}]+)\\.([^}]+)}}");

    private final Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVars;
    private final Map<String, Object> triggerInputs;
    private final Long actorUserId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private SseNotifier sseNotifier;
    private Long executionId;

    public ExecutionContext(Map<String, Object> triggerInputs, Map<String, Object> globalVars,
                            Long actorUserId) {
        this.globalVars = globalVars != null ? new HashMap<>(globalVars) : new HashMap<>();
        this.triggerInputs = triggerInputs != null ? new HashMap<>(triggerInputs) : new HashMap<>();
        this.actorUserId = actorUserId;
        nodeOutputs.put("__trigger__", this.triggerInputs);
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
                value = resolvePath(getNodeOutput(ref.nodeId(), ref.outputName()), ref.path());
            } else if (input.value() instanceof LiteralValue lit) {
                value = lit.value();
            } else if (input.value() instanceof TemplateValue tmpl) {
                value = resolveTemplate(tmpl.template());
            }
            resolved.put(input.name(), value);
        }
        return resolved;
    }

    /**
     * 解析模板字符串，将 {{nodeId.portName}} 替换为实际运行时值
     */
    private String resolveTemplate(String template) {
        if (template == null || template.isBlank()) return "";
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String nodeId = matcher.group(1);
            String portName = matcher.group(2);
            Object resolved = getNodeOutput(nodeId, portName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved != null ? String.valueOf(resolved) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 只支持显式的 Map key 或数组下标，不使用反射读取对象属性。 */
    private Object resolvePath(Object value, String path) {
        if (value == null || path == null || path.isBlank()) return value;
        Object current = value;
        for (String segment : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(segment);
                    current = index >= 0 && index < list.size() ? list.get(index) : null;
                } catch (NumberFormatException exception) {
                    return null;
                }
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
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
    public Map<String, Object> getTriggerInputs() { return Map.copyOf(triggerInputs); }
    public Long getActorUserId() { return actorUserId; }

    public Map<String, Map<String, Object>> snapshotNodeOutputs() {
        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        nodeOutputs.forEach((nodeId, outputs) -> snapshot.put(nodeId, new HashMap<>(outputs)));
        return snapshot;
    }

    public void restoreNodeOutputs(Map<String, Map<String, Object>> outputs) {
        if (outputs == null) return;
        outputs.forEach((nodeId, values) ->
                nodeOutputs.put(nodeId, values != null ? new ConcurrentHashMap<>(values) : new ConcurrentHashMap<>()));
    }
}

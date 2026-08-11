package com.trackflow.automation.node.nodes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.service.AutomationIssueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单字段更新节点：支持更新优先级、负责人、标签或自定义字段。
 */
@Component
@RequiredArgsConstructor
public class IssueUpdateNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;
    private final ObjectMapper objectMapper;

    @Override public String getType() { return "trackflow-issue-update"; }
    @Override public String getTitle() { return "更新工单字段"; }
    @Override public String getIcon() { return "✏️"; }
    @Override public String getColor() { return "#ec4899"; }
    @Override public String getDescription() { return "更新工单的优先级、负责人、标签或自定义字段"; }
    @Override public String getCategory() { return "TrackFlow"; }
    @Override public NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.sideEffect(); }

    @Override
    public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("issueId", "number", true, "工单 ID"),
                new InputPortDef("priority", "string", false, "优先级：critical/high/medium/low"),
                new InputPortDef("assigneeId", "number", false, "负责人用户 ID"),
                new InputPortDef("tagIds", "string", false, "标签 ID，逗号分隔（全量替换）"),
                new InputPortDef("customFields", "string", false, "JSON 格式自定义字段 {\"fieldName\":\"value\"}")
        );
    }

    @Override
    public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("issue", "object", "更新后的工单对象"),
                new OutputPortDef("success", "boolean", "是否成功")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        try {
            Long issueId = requiredLong(inputs.get("issueId"), "issueId");
            String priority = stringValue(inputs.get("priority"));
            Long assigneeId = longValue(inputs.get("assigneeId"));
            String tagIds = stringValue(inputs.get("tagIds"));
            Map<String, String> customFields = parseCustomFields(inputs.get("customFields"));

            Map<String, Object> issue = issueFacade.update(
                    context.getActorUserId(), issueId, priority, assigneeId, tagIds, customFields);
            return Map.of("issue", issue, "success", true);
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseCustomFields(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : null));
            return result;
        }
        String str = value.toString().trim();
        if (str.startsWith("{") && str.endsWith("}")) {
            try {
                return objectMapper.readValue(str,
                        new TypeReference<LinkedHashMap<String, String>>() {});
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Long requiredLong(Object value, String name) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private Long longValue(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value.toString();
    }
}

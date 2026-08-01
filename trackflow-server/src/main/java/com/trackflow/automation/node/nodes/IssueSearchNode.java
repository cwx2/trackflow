package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.service.AutomationIssueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueSearchNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;

    @Override public String getType() { return "trackflow-issue-search"; }
    @Override public String getTitle() { return "查找待办需求"; }
    @Override public String getIcon() { return "🔎"; }
    @Override public String getColor() { return "#0ea5e9"; }
    @Override public String getDescription() { return "按项目、状态、关键词和负责人筛选需求"; }
    @Override public String getCategory() { return "TrackFlow"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("projectId", "number", false, "项目 ID；留空时查询有权访问的项目"),
                new InputPortDef("statusIds", "string", false, "状态 ID，多个用逗号分隔"),
                new InputPortDef("keyword", "string", false, "标题、描述或编号关键词"),
                new InputPortDef("assignedToMe", "boolean", false, "只查分配给执行身份的工单"),
                new InputPortDef("limit", "number", false, "最多返回 100 条")
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("issues", "array", "工单列表"),
                new OutputPortDef("count", "number", "返回数量"),
                new OutputPortDef("hasWork", "boolean", "是否存在待处理工单")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        try {
            Long projectId = longValue(inputs.get("projectId"));
            String statusIds = stringValue(inputs.get("statusIds"));
            String keyword = stringValue(inputs.get("keyword"));
            boolean assignedToMe = booleanValue(inputs.get("assignedToMe"));
            int limit = intValue(inputs.get("limit"), 20);
            List<Map<String, Object>> issues = issueFacade.search(
                    context.getActorUserId(), projectId, statusIds, keyword, assignedToMe, limit);
            return Map.of("issues", issues, "count", issues.size(), "hasWork", !issues.isEmpty());
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }

    private Long longValue(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }
    private int intValue(Object value, int fallback) {
        if (value == null || value.toString().isBlank()) return fallback;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }
    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }
    private String stringValue(Object value) { return value != null ? value.toString() : null; }
}

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

/**
 * 工单搜索节点：支持按项目、状态、优先级、工单类型、标签、关键词等多维度筛选。
 */
@Component
@RequiredArgsConstructor
public class IssueSearchNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;

    @Override public String getType() { return "trackflow-issue-search"; }
    @Override public String getTitle() { return "查找待办需求"; }
    @Override public String getIcon() { return "🔎"; }
    @Override public String getColor() { return "#0ea5e9"; }
    @Override public String getDescription() { return "按项目、状态、优先级、类型和关键词筛选需求"; }
    @Override public String getCategory() { return "TrackFlow"; }

    @Override
    public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("projectId", "number", false, "项目 ID；留空时查询有权访问的项目", false),
                new InputPortDef("statusIds", "string", false, "状态 ID，多个用逗号分隔", false),
                new InputPortDef("priority", "string", false, "优先级，多个用逗号分隔（critical,high,medium,low）", false),
                new InputPortDef("issueType", "string", false, "工单类型，多个用逗号分隔（Bug,Feature,Task 等）", false),
                new InputPortDef("tagIds", "string", false, "标签 ID，多个用逗号分隔", false),
                new InputPortDef("savedQueryId", "number", false,
                        "完整保存筛选 ID；设置后按保存筛选（含自定义字段）查询", true),
                new InputPortDef("keyword", "string", false, "标题、描述或编号关键词", true),
                new InputPortDef("assignedToMe", "boolean", false, "只查分配给执行身份的工单", true),
                new InputPortDef("sort", "string", false, "排序：-priority（默认），created_at，-updated_at", true),
                new InputPortDef("limit", "number", false, "最多返回 100 条", true)
        );
    }

    @Override
    public List<OutputPortDef> getOutputPorts() {
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
            String priority = stringValue(inputs.get("priority"));
            String issueType = stringValue(inputs.get("issueType"));
            String tagIds = stringValue(inputs.get("tagIds"));
            Long savedQueryId = longValue(inputs.get("savedQueryId"));
            String keyword = stringValue(inputs.get("keyword"));
            boolean assignedToMe = booleanValue(inputs.get("assignedToMe"));
            String sort = stringValue(inputs.get("sort"));
            int limit = intValue(inputs.get("limit"), 20);

            List<Map<String, Object>> issues = savedQueryId != null
                    ? issueFacade.searchSavedQuery(context.getActorUserId(), savedQueryId, limit)
                    : issueFacade.search(context.getActorUserId(), projectId, statusIds, keyword,
                            assignedToMe, limit, priority, issueType, tagIds, sort);
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

    private String stringValue(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value.toString();
    }
}

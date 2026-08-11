package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.PortCardinality;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import com.trackflow.automation.service.AutomationIssueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IssueGetNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;

    @Override public String getType() { return "trackflow-issue-get"; }
    @Override public String getTitle() { return "获取需求"; }
    @Override public String getIcon() { return "📋"; }
    @Override public String getColor() { return "#2563eb"; }
    @Override public String getDescription() { return "按工单 ID 或编号读取 TrackFlow 需求"; }
    @Override public String getCategory() { return "TrackFlow"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("issue", "string", true, "工单 ID 或 REQ-编号"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(new OutputPortDef("issue", "工单（完整）", "object", "标准化工单对象",
                PortCardinality.single, "issue"));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        Object identifier = inputs.get("issue");
        if (identifier == null || identifier.toString().isBlank()) {
            throw new NodeExecutionException(node.id(), "获取需求节点必须提供工单 ID 或编号");
        }
        try {
            return Map.of("issue", issueFacade.getIssue(context.getActorUserId(), identifier));
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }
}

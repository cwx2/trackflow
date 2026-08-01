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
public class IssueTransitionNode implements NodeDefinition, NodeExecutor {
    private final AutomationIssueFacade issueFacade;

    @Override public String getType() { return "trackflow-issue-transition"; }
    @Override public String getTitle() { return "变更需求状态"; }
    @Override public String getIcon() { return "🔁"; }
    @Override public String getColor() { return "#f59e0b"; }
    @Override public String getDescription() { return "按 TrackFlow 状态机安全流转工单"; }
    @Override public String getCategory() { return "TrackFlow"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("issueId", "number", true, "工单 ID"),
                new InputPortDef("statusId", "number", true, "目标状态 ID"),
                new InputPortDef("comment", "string", false, "状态变更说明"),
                new InputPortDef("version", "number", false, "乐观锁版本")
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("issue", "object", "更新后的工单"),
                new OutputPortDef("success", "boolean", "流转成功分支")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                       ExecutionContext context) throws NodeExecutionException {
        try {
            Long issueId = requiredLong(inputs.get("issueId"), "issueId");
            Long statusId = requiredLong(inputs.get("statusId"), "statusId");
            Integer version = optionalInteger(inputs.get("version"));
            Map<String, Object> issue = issueFacade.transition(context.getActorUserId(), issueId,
                    statusId, inputs.get("comment") != null ? inputs.get("comment").toString() : null, version);
            return Map.of("issue", issue, "success", true);
        } catch (RuntimeException exception) {
            throw new NodeExecutionException(node.id(), exception.getMessage(), exception);
        }
    }

    private Long requiredLong(Object value, String name) {
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }
    private Integer optionalInteger(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }
}

package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConditionNode implements NodeDefinition, NodeExecutor {

    @Override public String getType()        { return "condition"; }
    @Override public String getTitle()       { return "条件判断"; }
    @Override public String getIcon()        { return "🔀"; }
    @Override public String getColor()       { return "#f59e0b"; }
    @Override public String getDescription() { return "根据条件决定分支流转"; }
    @Override public String getCategory()    { return "控制流"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("value", "string", true, "待判断的值"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
            new OutputPortDef("true",  "boolean", "条件成立"),
            new OutputPortDef("false", "boolean", "条件不成立")
        );
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs,
                                       WorkflowNodeModel node,
                                       ExecutionContext ctx) throws NodeExecutionException {
        String value        = String.valueOf(inputs.getOrDefault("value", ""));
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();
        String operator     = String.valueOf(config.getOrDefault("operator",    "contains"));
        String compareValue = String.valueOf(config.getOrDefault("compareValue", ""));

        boolean result = switch (operator) {
            case "contains"     -> value.contains(compareValue);
            case "not_contains" -> !value.contains(compareValue);
            case "equals"       -> value.equals(compareValue);
            case "not_equals"   -> !value.equals(compareValue);
            case "is_empty"     -> value.isBlank();
            case "is_not_empty" -> !value.isBlank();
            default             -> throw new NodeExecutionException(node.id(), "不支持的条件操作符: " + operator);
        };

        return Map.of("true", result, "false", !result);
    }
}

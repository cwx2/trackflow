package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StartNode implements NodeDefinition, NodeExecutor {
    @Override public String getType()        { return "start"; }
    @Override public String getTitle()       { return "开始"; }
    @Override public String getIcon()        { return "▶"; }
    @Override public String getColor()       { return "#10b981"; }
    @Override public String getDescription() { return "工作流触发入口"; }
    @Override public String getCategory()    { return "特殊节点"; }
    @Override public List<InputPortDef>  getInputPorts()  { return List.of(); }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(new OutputPortDef("trigger", "object", "触发参数"));
    }
    @Override
    public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node, ExecutionContext ctx) {
        return Map.of("trigger", inputs.isEmpty() ? Map.of() : inputs);
    }
}

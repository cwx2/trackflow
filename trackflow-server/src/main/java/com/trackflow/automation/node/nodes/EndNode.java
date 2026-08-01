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
public class EndNode implements NodeDefinition, NodeExecutor {

    @Override public String getType()        { return "end"; }
    @Override public String getTitle()       { return "结束"; }
    @Override public String getIcon()        { return "⏹"; }
    @Override public String getColor()       { return "#ef4444"; }
    @Override public String getDescription() { return "工作流终点，收集最终结果"; }
    @Override public String getCategory()    { return "特殊节点"; }
    @Override public List<InputPortDef>  getInputPorts()  {
        return List.of(new InputPortDef("result", "object", false, "工作流最终输出"));
    }
    @Override public List<OutputPortDef> getOutputPorts() { return List.of(); }

    @Override
    public Map<String, Object> execute(Map<String, Object> inputs,
                                       WorkflowNodeModel node,
                                       ExecutionContext ctx) {
        return Map.copyOf(inputs);
    }
}

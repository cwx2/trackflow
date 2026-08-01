package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SubWorkflowNode implements NodeDefinition, NodeExecutor {
    @Override public String getType() { return "sub-workflow"; }
    @Override public String getTitle() { return "子工作流"; }
    @Override public String getIcon() { return "🔗"; }
    @Override public String getColor() { return "#d97706"; }
    @Override public String getDescription() { return "调用已发布工作流并持久化等待结果"; }
    @Override public String getCategory() { return "业务逻辑"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("input", "object", false, "传给子工作流的输入"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("output", "object", "子工作流输出"),
                new OutputPortDef("status", "string", "子执行状态"),
                new OutputPortDef("success", "boolean", "成功分支")
        );
    }
    @Override public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                                ExecutionContext context) throws NodeExecutionException {
        throw new NodeExecutionException(node.id(), "子工作流节点必须由持久化执行器处理");
    }
}

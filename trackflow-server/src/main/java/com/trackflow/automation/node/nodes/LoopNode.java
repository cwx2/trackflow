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

/** 有界循环通过重复调用已发布子工作流实现，不在 DAG 中制造回边。 */
@Component
public class LoopNode implements NodeDefinition, NodeExecutor {
    @Override public String getType() { return "loop"; }
    @Override public String getTitle() { return "有界循环"; }
    @Override public String getIcon() { return "🔄"; }
    @Override public String getColor() { return "#10b981"; }
    @Override public String getDescription() { return "重复执行子工作流，直到满足条件或达到上限"; }
    @Override public String getCategory() { return "控制流"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef("input", "object", false, "每轮传给子工作流的输入"));
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("output", "object", "最后一次子工作流输出"),
                new OutputPortDef("iteration", "number", "执行轮次"),
                new OutputPortDef("success", "boolean", "满足退出条件"),
                new OutputPortDef("exhausted", "boolean", "达到最大次数")
        );
    }
    @Override public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                                ExecutionContext context) throws NodeExecutionException {
        throw new NodeExecutionException(node.id(), "循环节点必须由持久化执行器处理");
    }
}

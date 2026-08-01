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

/** 实际挂起由 DAGExecutor 持久化处理。 */
@Component
public class ApprovalNode implements NodeDefinition, NodeExecutor {
    @Override public String getType() { return "approval"; }
    @Override public String getTitle() { return "人工审批"; }
    @Override public String getIcon() { return "🛡️"; }
    @Override public String getColor() { return "#dc2626"; }
    @Override public String getDescription() { return "高风险写操作前挂起，审批后从检查点恢复"; }
    @Override public String getCategory() { return "控制流"; }
    @Override public List<InputPortDef> getInputPorts() {
        return List.of(
                new InputPortDef("title", "string", true, "审批标题"),
                new InputPortDef("description", "string", false, "风险和影响说明"),
                new InputPortDef("payload", "object", false, "供审批人检查的数据")
        );
    }
    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("approved", "boolean", "批准分支"),
                new OutputPortDef("rejected", "boolean", "拒绝分支"),
                new OutputPortDef("comment", "string", "审批意见")
        );
    }
    @Override public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                                ExecutionContext context) throws NodeExecutionException {
        throw new NodeExecutionException(node.id(), "审批节点必须由持久化执行器处理");
    }
}

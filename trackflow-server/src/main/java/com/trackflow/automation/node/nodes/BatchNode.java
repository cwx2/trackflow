package com.trackflow.automation.node.nodes;

import com.trackflow.automation.execution.ExecutionContext;
import com.trackflow.automation.node.NodeDefinition;
import com.trackflow.automation.node.NodeExecutionException;
import com.trackflow.automation.node.NodeExecutor;
import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.PortCardinality;
import com.trackflow.automation.node.model.WorkflowNodeModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 集合到单条数据的显式边界。
 *
 * <p>每个元素会作为一次独立的已持久化子工作流执行，父流程在子流程完成后
 * 从检查点继续。因此重启后不会丢失进度，也不会把集合悄悄塞进单条端口。</p>
 */
@Component
public class BatchNode implements NodeDefinition, NodeExecutor {
    @Override public String getType() { return "batch"; }
    @Override public String getTitle() { return "批处理"; }
    @Override public String getIcon() { return "↻"; }
    @Override public String getColor() { return "#7c3aed"; }
    @Override public String getDescription() { return "将集合逐项交给已发布的子工作流，并汇总成功与失败结果"; }
    @Override public String getCategory() { return "控制流"; }
    @Override public NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.orchestration(); }

    @Override public void validateConfiguration(WorkflowNodeModel node, boolean executable) {
        if (!executable) return;
        Object workflowId = node.config() != null ? node.config().get("workflowId") : null;
        if (workflowId == null || workflowId.toString().isBlank()) {
            throw new IllegalArgumentException("批处理节点必须选择一个已发布的处理子工作流: " + node.id());
        }
        try {
            if (Long.parseLong(workflowId.toString()) <= 0) {
                throw new IllegalArgumentException("批处理子工作流 ID 必须为正整数: " + node.id());
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("批处理子工作流 ID 无效: " + node.id());
        }
        Object maxItems = node.config().get("maxItems");
        if (maxItems != null) {
            try {
                int value = maxItems instanceof Number number ? number.intValue()
                        : Integer.parseInt(maxItems.toString());
                if (value < 1 || value > 1000) {
                    throw new IllegalArgumentException("批处理单次最多处理范围为 1-1000: " + node.id());
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("批处理单次最多处理必须是整数: " + node.id());
            }
        }
    }

    @Override public List<InputPortDef> getInputPorts() {
        return List.of(new InputPortDef(
                "items", "待处理列表", "array", true,
                "来自上游的集合；每一项会作为子工作流的一次输入", false,
                PortCardinality.collection, null));
    }

    @Override public List<OutputPortDef> getOutputPorts() {
        return List.of(
                new OutputPortDef("successItems", "成功项", "array", "子流程成功的每一项及其输出",
                        PortCardinality.collection, null),
                new OutputPortDef("failedItems", "失败项", "array", "子流程失败的每一项及错误信息",
                        PortCardinality.collection, null),
                new OutputPortDef("skippedItems", "跳过项", "array", "超过数量上限而未执行的项目",
                        PortCardinality.collection, null),
                new OutputPortDef("summary", "执行汇总", "object", "总数、成功数、失败数与跳过数",
                        PortCardinality.single, null)
        );
    }

    @Override public Map<String, Object> execute(Map<String, Object> inputs, WorkflowNodeModel node,
                                                  ExecutionContext context) throws NodeExecutionException {
        throw new NodeExecutionException(node.id(), "批处理节点必须由持久化执行器处理");
    }
}

package com.trackflow.automation.node;

import com.trackflow.automation.node.model.InputPortDef;
import com.trackflow.automation.node.model.NodeContract;
import com.trackflow.automation.node.model.NodeObservabilityPolicy;
import com.trackflow.automation.node.model.NodeRuntimePolicy;
import com.trackflow.automation.node.model.OutputPortDef;
import com.trackflow.automation.node.model.WorkflowNodeModel;

import java.util.List;

public interface NodeDefinition {
    String getType();
    String getTitle();
    String getIcon();
    String getColor();
    String getDescription();
    default String getCategory() { return "基础节点"; }
    List<InputPortDef> getInputPorts();
    List<OutputPortDef> getOutputPorts();

    /** 节点运行策略在节点自身声明，禁止由调用方维护按 type 分支的名单。 */
    default NodeRuntimePolicy getRuntimePolicy() { return NodeRuntimePolicy.safe(); }

    /** 调试与审计需要展示或脱敏的字段由节点自身声明。 */
    default NodeObservabilityPolicy getObservabilityPolicy() { return NodeObservabilityPolicy.standard(); }

    /**
     * 校验节点特有配置。草稿阶段允许暂未配置，发布和试运行前必须完整。
     * 具体节点把自己的配置约束留在定义中，避免执行器维护一份易遗漏的 type 分支表。
     */
    default void validateConfiguration(WorkflowNodeModel node, boolean executable) {}

    default NodeContract getContract() {
        return new NodeContract(getType(), getTitle(), getIcon(), getColor(), getDescription(), getCategory(),
                getInputPorts(), getOutputPorts(), getRuntimePolicy(), getObservabilityPolicy());
    }
}

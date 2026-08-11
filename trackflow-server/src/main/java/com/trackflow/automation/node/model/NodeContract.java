package com.trackflow.automation.node.model;

import java.util.List;

/**
 * 可执行节点的完整公开契约。
 *
 * 节点目录接口、草稿校验、试运行策略和前端画布都应从这里取得同一事实来源。
 */
public record NodeContract(
        String type,
        String title,
        String icon,
        String color,
        String description,
        String category,
        List<InputPortDef> inputPorts,
        List<OutputPortDef> outputPorts,
        NodeRuntimePolicy runtime,
        NodeObservabilityPolicy observability) {
    public NodeContract {
        inputPorts = inputPorts == null ? List.of() : List.copyOf(inputPorts);
        outputPorts = outputPorts == null ? List.of() : List.copyOf(outputPorts);
        runtime = runtime == null ? NodeRuntimePolicy.safe() : runtime;
        observability = observability == null ? NodeObservabilityPolicy.standard() : observability;
    }
}

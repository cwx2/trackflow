package com.trackflow.automation.node.model;

import java.util.List;

/**
 * 供调试面板与执行记录使用的可观测性声明。
 * 敏感字段由消费者脱敏；摘要字段让 UI 无须猜测如何展示大对象。
 */
public record NodeObservabilityPolicy(
        List<String> inputSummaryFields,
        List<String> outputSummaryFields,
        List<String> sensitiveInputFields) {
    public NodeObservabilityPolicy {
        inputSummaryFields = inputSummaryFields == null ? List.of() : List.copyOf(inputSummaryFields);
        outputSummaryFields = outputSummaryFields == null ? List.of() : List.copyOf(outputSummaryFields);
        sensitiveInputFields = sensitiveInputFields == null ? List.of() : List.copyOf(sensitiveInputFields);
    }

    public static NodeObservabilityPolicy standard() {
        return new NodeObservabilityPolicy(List.of(), List.of(), List.of());
    }
}

package com.trackflow.automation.node.model;

/**
 * 对上游输出的显式引用。path 用于从 object/array 输出中取字段，例如
 * start.trigger 的 issueId 为 path="issueId"。
 */
public record VariableRef(String type, String nodeId, String outputName, String path) implements InputValue {
    public VariableRef(String nodeId, String outputName) { this("ref", nodeId, outputName, null); }
    public VariableRef(String nodeId, String outputName, String path) {
        this("ref", nodeId, outputName, path);
    }
}

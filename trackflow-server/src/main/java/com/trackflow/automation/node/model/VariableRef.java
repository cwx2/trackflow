package com.trackflow.automation.node.model;

public record VariableRef(String type, String nodeId, String outputName) implements InputValue {
    public VariableRef(String nodeId, String outputName) { this("ref", nodeId, outputName); }
}

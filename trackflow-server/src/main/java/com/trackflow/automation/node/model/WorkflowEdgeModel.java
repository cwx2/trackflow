package com.trackflow.automation.node.model;

public record WorkflowEdgeModel(
    String id, String sourceNodeId, String sourcePortName, String targetNodeId, String targetPortName
) {}

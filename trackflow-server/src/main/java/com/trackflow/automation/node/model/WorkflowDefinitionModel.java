package com.trackflow.automation.node.model;

import java.util.List;
import java.util.Map;

public record WorkflowDefinitionModel(
    Map<String, GlobalVariable> globalVariables,
    List<WorkflowNodeModel> nodes,
    List<WorkflowEdgeModel> edges
) {
    public record GlobalVariable(String valueType, Object defaultValue) {}
}

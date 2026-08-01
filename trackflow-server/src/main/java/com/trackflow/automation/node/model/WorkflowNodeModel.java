package com.trackflow.automation.node.model;

import java.util.List;
import java.util.Map;

public record WorkflowNodeModel(
    String id, String type, Position position, NodeMeta nodeMeta,
    List<InputParameter> inputs, List<OutputPortDef> outputs, Map<String, Object> config
) {
    public record Position(double x, double y) {}
    public record NodeMeta(String title, String icon, String description, String color) {}
}

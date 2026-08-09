package com.trackflow.automation.node.model;

import java.util.List;
import java.util.Map;

public record WorkflowNodeModel(
    String id, String type, Position position, NodeMeta nodeMeta,
    List<InputParameter> inputs, List<OutputPortDef> outputs, Map<String, Object> config
) {
    public record Position(double x, double y) {}
    /**
     * 节点的展示元数据同样属于工作流定义契约，而不只是前端临时状态。
     * category 用于节点库分组和模板展示，必须和前端定义一起持久化、传输。
     */
    public record NodeMeta(String title, String icon, String description, String color, String category) {}
}

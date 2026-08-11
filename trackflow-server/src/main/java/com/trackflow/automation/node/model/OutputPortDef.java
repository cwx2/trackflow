package com.trackflow.automation.node.model;

/** 节点输出端口的完整持久化模型。 */
public record OutputPortDef(String name, String label, String valueType, String description,
                            PortCardinality cardinality, String semanticType) {
    public OutputPortDef {
        cardinality = cardinality == null ? PortCardinality.fromValueType(valueType) : cardinality;
    }
    /** 节点实现中的简写；持久化后仍会有明确的 label。 */
    public OutputPortDef(String name, String valueType, String description) {
        this(name, name, valueType, description, null, null);
    }
}

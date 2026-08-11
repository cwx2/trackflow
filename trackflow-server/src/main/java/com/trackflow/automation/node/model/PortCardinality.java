package com.trackflow.automation.node.model;

/** 端口传递单个值还是一组值；集合不能在没有批处理语义时直接流入单值端口。 */
public enum PortCardinality {
    single,
    collection;

    public static PortCardinality fromValueType(String valueType) {
        return "array".equals(valueType) ? collection : single;
    }
}

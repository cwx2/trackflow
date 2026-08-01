package com.trackflow.automation.node.model;

public record InputPortDef(String name, String valueType, boolean required, String description) {
    public InputPortDef(String name, String valueType, boolean required) {
        this(name, valueType, required, "");
    }
}

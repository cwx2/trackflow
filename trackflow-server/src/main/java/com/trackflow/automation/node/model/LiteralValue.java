package com.trackflow.automation.node.model;

public record LiteralValue(String type, Object value) implements InputValue {
    public LiteralValue(Object value) { this("literal", value); }
}

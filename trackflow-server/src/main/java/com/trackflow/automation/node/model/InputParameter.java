package com.trackflow.automation.node.model;

public record InputParameter(
    String name, String valueType, boolean required, String description, InputValue value
) {}

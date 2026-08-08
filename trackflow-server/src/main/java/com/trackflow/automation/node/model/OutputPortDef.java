package com.trackflow.automation.node.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 运行时输出端口；忽略仅供画布展示的 label 等扩展字段。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutputPortDef(String name, String valueType, String description) {}

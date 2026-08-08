package com.trackflow.automation.node.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 运行时输入槽。画布会额外保存 label、optional 等展示元数据；
 * 这些字段不参与执行，必须允许跨版本工作流继续反序列化。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InputParameter(
    String name, String valueType, boolean required, String description, InputValue value
) {}

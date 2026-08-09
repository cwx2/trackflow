package com.trackflow.automation.node.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 输入参数的值 —— 字面值、变量引用或模板表达式
 * sealed interface 保证只有这三种实现
 */
// type 既是多态判别字段，也是各 value record 的正式契约字段；必须保留给 record 绑定。
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = LiteralValue.class, name = "literal"),
    @JsonSubTypes.Type(value = VariableRef.class, name = "ref"),
    @JsonSubTypes.Type(value = TemplateValue.class, name = "template"),
})
public sealed interface InputValue permits LiteralValue, VariableRef, TemplateValue {}

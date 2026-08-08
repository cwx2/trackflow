package com.trackflow.automation.node.model;

/**
 * 输入参数的值 —— 字面值、变量引用或模板表达式
 * sealed interface 保证只有这三种实现
 */
public sealed interface InputValue permits LiteralValue, VariableRef, TemplateValue {}

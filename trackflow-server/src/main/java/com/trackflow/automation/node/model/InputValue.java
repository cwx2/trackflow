package com.trackflow.automation.node.model;

/**
 * 输入参数的值 —— 字面值或变量引用
 * sealed interface 保证只有这两种实现
 */
public sealed interface InputValue permits LiteralValue, VariableRef {}

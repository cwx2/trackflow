package com.trackflow.automation.node.model;

/**
 * 模板表达式值 — 支持字面文本和变量引用的混合模板
 * 
 * 格式：字符串中使用 {{nodeId.portName}} 引用上游变量
 * 例如："审核以下需求：{{issue_context_1.context}}"
 */
public record TemplateValue(String type, String template) implements InputValue {
    public TemplateValue(String template) {
        this("template", template);
    }
}

package com.trackflow.automation.node.model;

/**
 * 节点输入槽的完整持久化模型。
 *
 * <p>画布展示字段不是“未知兼容字段”，而是工作流定义的一部分。保存、预览和执行
 * 使用同一个模型，避免前端写出的定义在后端无法读取。</p>
 */
public record InputParameter(
        String name,
        String label,
        String valueType,
        boolean required,
        String description,
        boolean optional,
        InputValue value
) {}

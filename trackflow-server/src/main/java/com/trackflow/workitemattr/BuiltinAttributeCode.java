package com.trackflow.workitemattr;

/**
 * 内建工作项属性的系统代码常量。
 * 用于动态查找属性 ID，消除硬编码依赖。
 */
public final class BuiltinAttributeCode {

    private BuiltinAttributeCode() {
    }

    /**
     * Work Type 属性 —— 工时分类（如开发、测试、会议等）
     */
    public static final String WORK_TYPE = "WORK_TYPE";
}

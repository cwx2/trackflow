package com.trackflow.automation.node.model;

/** 节点级试运行的安全策略。 */
public enum NodeTestMode {
    /** 可以直接执行，不产生持久化或外部副作用。 */
    safe,
    /** 必须由用户明确确认后才执行。 */
    confirm,
    /** 只能预演；真实行为依赖完整工作流的持久化编排上下文。 */
    simulated
}

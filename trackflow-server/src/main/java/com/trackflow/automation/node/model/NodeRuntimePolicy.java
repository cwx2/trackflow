package com.trackflow.automation.node.model;

/**
 * 节点的运行约束。
 * retrySafe=false 表示自动重试可能重复产生副作用，执行器必须将其限制为一次。
 */
public record NodeRuntimePolicy(int defaultMaxAttempts, boolean retrySafe, NodeTestMode testMode) {
    public NodeRuntimePolicy {
        defaultMaxAttempts = Math.max(1, Math.min(defaultMaxAttempts, 10));
        testMode = testMode == null ? NodeTestMode.safe : testMode;
    }

    public static NodeRuntimePolicy safe() {
        return new NodeRuntimePolicy(1, true, NodeTestMode.safe);
    }

    public static NodeRuntimePolicy sideEffect() {
        return new NodeRuntimePolicy(1, false, NodeTestMode.confirm);
    }

    public static NodeRuntimePolicy orchestration() {
        return new NodeRuntimePolicy(1, true, NodeTestMode.simulated);
    }
}

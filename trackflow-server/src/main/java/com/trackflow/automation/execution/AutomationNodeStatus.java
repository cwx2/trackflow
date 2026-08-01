package com.trackflow.automation.execution;

/**
 * 自动化节点执行状态。
 */
public enum AutomationNodeStatus {
    PENDING("pending"),
    READY("ready"),
    RUNNING("running"),
    WAITING("waiting"),
    RETRYING("retrying"),
    SUCCESS("success"),
    FAILED("failed"),
    SKIPPED("skipped"),
    CANCELLED("cancelled");

    private final String value;

    AutomationNodeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

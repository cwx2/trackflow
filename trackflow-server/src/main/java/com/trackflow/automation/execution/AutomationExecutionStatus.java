package com.trackflow.automation.execution;

/**
 * 自动化工作流执行状态。
 */
public enum AutomationExecutionStatus {
    QUEUED("queued"),
    RUNNING("running"),
    WAITING_TIMER("waiting_timer"),
    WAITING_EVENT("waiting_event"),
    WAITING_APPROVAL("waiting_approval"),
    RETRYING("retrying"),
    PAUSED("paused"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    AutomationExecutionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }

    public static AutomationExecutionStatus fromValue(String value) {
        for (AutomationExecutionStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        throw new IllegalArgumentException("未知的自动化执行状态: " + value);
    }
}

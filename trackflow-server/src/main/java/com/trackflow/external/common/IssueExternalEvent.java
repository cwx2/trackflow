package com.trackflow.external.common;

/**
 * Issue 相关的出站事件。
 * <p>
 * 由 {@link ExternalEventBridge} 在监听到核心 Issue 事件后构造。
 */
public class IssueExternalEvent extends ExternalEvent {

    private IssueExternalEvent(String adapterType, String eventType, String referenceId, String payload) {
        super(adapterType, eventType, Direction.OUTBOUND, referenceId, payload);
    }

    /**
     * 构造工单创建事件
     */
    public static IssueExternalEvent created(String referenceId, String payload) {
        return new IssueExternalEvent("*", ExternalEventType.ISSUE_CREATED.getCode(), referenceId, payload);
    }

    /**
     * 构造工单状态变更事件
     */
    public static IssueExternalEvent statusChanged(String referenceId, String payload) {
        return new IssueExternalEvent("*", ExternalEventType.ISSUE_STATUS_CHANGED.getCode(), referenceId, payload);
    }

    /**
     * 构造工单取消事件
     */
    public static IssueExternalEvent cancelled(String referenceId, String payload) {
        return new IssueExternalEvent("*", ExternalEventType.ISSUE_CANCELLED.getCode(), referenceId, payload);
    }

    /**
     * 构造工单分配事件
     */
    public static IssueExternalEvent assigned(String referenceId, String payload) {
        return new IssueExternalEvent("*", ExternalEventType.ISSUE_ASSIGNED.getCode(), referenceId, payload);
    }

    /**
     * 构造评论创建事件
     */
    public static IssueExternalEvent commentCreated(String referenceId, String payload) {
        return new IssueExternalEvent("*", ExternalEventType.COMMENT_CREATED.getCode(), referenceId, payload);
    }
}

package com.trackflow.external.common;

/**
 * 外部集成事件类型枚举。
 * <p>
 * 定义所有可能触发外部集成的业务事件类型。
 * 适配器可以订阅其中的部分或全部事件类型。
 */
public enum ExternalEventType {

    // === Issue 相关 ===
    ISSUE_CREATED("issue.created", "工单创建"),
    ISSUE_STATUS_CHANGED("issue.status_changed", "工单状态变更"),
    ISSUE_CANCELLED("issue.cancelled", "工单取消"),
    ISSUE_ASSIGNED("issue.assigned", "工单分配"),
    ISSUE_DELETED("issue.deleted", "工单删除"),

    // === Comment 相关 ===
    COMMENT_CREATED("comment.created", "评论创建"),

    // === Apply 相关（预留） ===
    APPLY_CREATED("apply.created", "申请创建"),

    // === Project 相关 ===
    PROJECT_CREATED("project.created", "项目创建"),
    PROJECT_ARCHIVED("project.archived", "项目归档"),

    // === Sprint 相关 ===
    SPRINT_ACTIVATED("sprint.activated", "迭代激活"),
    SPRINT_COMPLETED("sprint.completed", "迭代完成");

    private final String code;
    private final String description;

    ExternalEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 查找枚举值
     */
    public static ExternalEventType fromCode(String code) {
        for (ExternalEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

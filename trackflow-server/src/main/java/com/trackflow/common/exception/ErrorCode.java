package com.trackflow.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码定义
 * <p>
 * 所有业务错误码必须在此枚举中定义，禁止在代码中硬编码数字。
 * 前端对应常量文件：src/api/error-codes.ts
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Validation (400xx)
    VALIDATION_ERROR(40000, 400, "请求参数校验失败"),
    BAD_REQUEST(40001, 400, "请求参数错误"),
    INVALID_BATCH_OPERATION(40002, 400, "无效的批量操作类型或缺少必填参数"),
    PROJECT_ARCHIVED(40003, 400, "归档项目不允许此操作"),
    QUIET_HOURS_INCOMPLETE(40004, 400, "静音时段的开始和结束时间必须同时设置或同时清除"),

    // Authentication (401xx)
    AUTH_MISSING(40100, 401, "未认证，请先登录"),
    TOKEN_EXPIRED(40101, 401, "登录已过期，请重新登录"),
    TOKEN_INVALID_ISSUER(40102, 401, "Token 签发者无效"),

    // Rate Limiting (429xx)
    RATE_LIMITED(42900, 429, "请求过于频繁，请稍后重试"),
    AUTH_RATE_LIMITED(42901, 429, "认证失败次数过多，您的 IP 已被临时封禁"),

    // Authorization (403xx)
    ACCESS_DENIED(40300, 403, "权限不足"),
    PROJECT_ACCESS_DENIED(40300, 403, "无权访问该项目"), // 与 ACCESS_DENIED 共用 code，前端无需区分
    OWNERSHIP_REQUIRED(40301, 403, "只能操作分配给自己或由自己创建的资源"),
    WORKFLOW_TRANSITION_DENIED(40302, 403, "当前角色不允许执行此状态转换"),
    USER_DISABLED(40303, 403, "用户账号已被禁用"),
    BUILTIN_ROLE_PROTECTED(40304, 403, "内置角色不允许修改"),
    PRIVILEGE_ESCALATION_DENIED(40305, 403, "您不能授予自己不持有的权限"),

    // Not Found (404xx)
    RESOURCE_NOT_FOUND(40400, 404, "资源不存在"),

    // Conflict (409xx)
    CONFLICT(40900, 409, "资源冲突"),
    DUPLICATE_RESOURCE(40906, 409, "资源已存在"),
    ORG_HAS_REFERENCES(40901, 409, "组织下仍有关联的用户或项目"),
    ORG_CODE_DUPLICATE(40902, 409, "组织编码已存在"),
    ROLE_IN_USE(40903, 409, "角色正在使用中，无法删除"),
    ROLE_CODE_DUPLICATE(40904, 409, "角色编码已存在"),
    PROJECT_KEY_DUPLICATE(40905, 409, "项目标识已存在"),
    CLOSE_CONFIRMATION_REQUIRED(40910, 409, "关闭前需要用户确认"),
    SPRINT_DATE_OVERLAP(40913, 409, "Sprint 日期与已有迭代重叠"),
    WIP_LIMIT_EXCEEDED(40914, 409, "目标列已达到 WIP 上限"),
    WORKFLOW_VERSION_CONFLICT(40911, 409, "工作流已被其他人修改，请刷新后重试"),
    BOARD_CONFIG_VERSION_CONFLICT(40912, 409, "看板配置已被其他人修改，请刷新后重试"),

    // Server (500xx)
    INTERNAL_ERROR(50000, 500, "服务器内部错误");

    private final int code;
    private final int httpStatus;
    private final String message;
}

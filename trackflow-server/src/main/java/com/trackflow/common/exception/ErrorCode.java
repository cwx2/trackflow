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
    VALIDATION_ERROR(40000, 400, "Request validation failed"),
    BAD_REQUEST(40001, 400, "Bad request"),
    INVALID_BATCH_OPERATION(40002, 400, "无效的批量操作类型或缺少必填参数"),
    PROJECT_ARCHIVED(40003, 400, "归档项目不允许此操作"),
    QUIET_HOURS_INCOMPLETE(40004, 400, "静音时段的开始和结束时间必须同时设置或同时清除"),

    // Authentication (401xx)
    AUTH_MISSING(40100, 401, "Authentication required"),
    TOKEN_EXPIRED(40101, 401, "Token has expired"),
    TOKEN_INVALID_ISSUER(40102, 401, "Invalid token issuer"),

    // Authorization (403xx)
    ACCESS_DENIED(40300, 403, "Insufficient permissions"),
    PROJECT_ACCESS_DENIED(40300, 403, "无权访问该项目"), // 与 ACCESS_DENIED 共用 code，前端无需区分
    OWNERSHIP_REQUIRED(40301, 403, "只能操作分配给自己或由自己创建的资源"),
    WORKFLOW_TRANSITION_DENIED(40302, 403, "当前角色不允许执行此状态转换"),
    USER_DISABLED(40303, 403, "User account is disabled"),
    BUILTIN_ROLE_PROTECTED(40304, 403, "Built-in roles cannot be modified"),

    // Not Found (404xx)
    RESOURCE_NOT_FOUND(40400, 404, "Resource not found"),

    // Conflict (409xx)
    CONFLICT(40900, 409, "Resource conflict"),
    ORG_HAS_REFERENCES(40901, 409, "Organization has associated users or projects"),
    ORG_CODE_DUPLICATE(40902, 409, "Organization code already exists"),
    ROLE_IN_USE(40903, 409, "Role is currently assigned and cannot be deleted"),
    ROLE_CODE_DUPLICATE(40904, 409, "Role code already exists"),
    PROJECT_KEY_DUPLICATE(40905, 409, "项目标识已存在"),
    CLOSE_CONFIRMATION_REQUIRED(40910, 409, "关闭前需要用户确认"),

    // Server (500xx)
    INTERNAL_ERROR(50000, 500, "Internal server error");

    private final int code;
    private final int httpStatus;
    private final String message;
}

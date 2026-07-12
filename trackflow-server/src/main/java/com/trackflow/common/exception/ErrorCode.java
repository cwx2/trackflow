package com.trackflow.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码定义
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Validation
    VALIDATION_ERROR(40000, 400, "Request validation failed"),
    BAD_REQUEST(40001, 400, "Bad request"),

    // Authentication
    AUTH_MISSING(40100, 401, "Authentication required"),
    TOKEN_EXPIRED(40101, 401, "Token has expired"),
    TOKEN_INVALID_ISSUER(40102, 401, "Invalid token issuer"),

    // Authorization
    ACCESS_DENIED(40300, 403, "Insufficient permissions"),
    PROJECT_ACCESS_DENIED(40303, 403, "无权访问该项目"),
    USER_DISABLED(40301, 403, "User account is disabled"),
    BUILTIN_ROLE_PROTECTED(40302, 403, "Built-in roles cannot be modified"),

    // Conflict
    ORG_HAS_REFERENCES(40901, 409, "Organization has associated users or projects"),
    ORG_CODE_DUPLICATE(40902, 409, "Organization code already exists"),
    ROLE_IN_USE(40903, 409, "Role is currently assigned and cannot be deleted"),
    ROLE_CODE_DUPLICATE(40904, 409, "Role code already exists"),

    // Not Found
    RESOURCE_NOT_FOUND(40400, 404, "Resource not found"),

    // Server
    INTERNAL_ERROR(50000, 500, "Internal server error");

    private final int code;
    private final int httpStatus;
    private final String message;
}

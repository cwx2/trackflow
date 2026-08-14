package com.trackflow.common.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.data = data;
    }

    public BusinessException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = null;
    }

    // ===== 静态工厂方法 =====

    /**
     * 资源不存在异常，格式："{entityName}不存在: {id}"
     * <pre>
     *   throw BusinessException.notFound("工单", issueId);
     *   throw BusinessException.notFound("自定义字段", fieldId);
     * </pre>
     */
    public static BusinessException notFound(String entityName, Object id) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, entityName + "不存在: " + id);
    }

    /**
     * 资源不存在异常（不带 ID，仅描述）
     * <pre>
     *   throw BusinessException.notFound("工单不存在");
     * </pre>
     */
    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}

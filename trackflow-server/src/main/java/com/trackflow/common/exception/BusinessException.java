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
}

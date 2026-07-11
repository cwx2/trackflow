package com.trackflow.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.trackflow.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一 API 响应封装（新版）
 *
 * @param <T> 响应数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private Long timestamp;
    private String traceId;

    private R() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = MDC.get("traceId");
    }

    private R(int code, String message, T data) {
        this();
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(0, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}

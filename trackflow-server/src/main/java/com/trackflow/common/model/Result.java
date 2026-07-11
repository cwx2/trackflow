package com.trackflow.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一 API 响应封装
 *
 * @deprecated 请使用 {@link R} 替代
 */
@Deprecated
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public Result() {
        this.traceId = MDC.get("traceId");
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }
}

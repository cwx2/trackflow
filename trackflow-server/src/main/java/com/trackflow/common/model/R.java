package com.trackflow.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.trackflow.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;
import java.util.List;

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
    /** 字段级警告信息（部分字段因权限不足被跳过时填充） */
    private List<String> warnings;

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

    /**
     * 成功响应，附带警告信息（部分字段被跳过时使用）
     */
    public static <T> R<T> okWithWarnings(T data, List<String> warnings) {
        R<T> r = new R<>(0, "success", data);
        r.warnings = (warnings != null && !warnings.isEmpty()) ? warnings : null;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 使用枚举错误码 + 自定义消息返回失败响应。
     * 保留枚举的 code 数值，覆盖默认 message。
     */
    public static <T> R<T> fail(ErrorCode errorCode, String customMessage) {
        return new R<>(errorCode.getCode(), customMessage, null);
    }
}

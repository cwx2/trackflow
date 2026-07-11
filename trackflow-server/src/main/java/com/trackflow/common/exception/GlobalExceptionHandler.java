package com.trackflow.common.exception;

import com.trackflow.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 所有异常统一转为 R 响应
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(R.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 权限不足
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(ErrorCode.ACCESS_DENIED.getCode(), "权限不足"));
    }

    /**
     * 认证失败
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ErrorCode.AUTH_MISSING.getCode(), "未认证"));
    }

    /**
     * 非预期异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误"));
    }
}

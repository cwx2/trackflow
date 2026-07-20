package com.trackflow.common.exception;

import com.trackflow.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
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
                .body(R.fail(ErrorCode.VALIDATION_ERROR, message));
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Object>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: code={}, message={}", ex.getCode(), ex.getMessage());
        R<Object> response = R.fail(ex.getCode(), ex.getMessage());
        if (ex.getData() != null) {
            response.setData(ex.getData());
        }
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 权限不足
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(ErrorCode.ACCESS_DENIED, "权限不足，您没有执行此操作的权限"));
    }

    /**
     * 认证失败
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.fail(ErrorCode.AUTH_MISSING, "未认证"));
    }

    /**
     * 资源不存在（404）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                   HttpServletRequest request) {
        log.warn("Resource not found: {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(ErrorCode.RESOURCE_NOT_FOUND, "请求的资源不存在"));
    }

    /**
     * 参数格式异常（如路径变量非法、数字格式错误）
     */
    @ExceptionHandler({NumberFormatException.class, IllegalArgumentException.class})
    public ResponseEntity<R<Void>> handleBadFormatException(Exception ex, HttpServletRequest request) {
        log.warn("Bad request format on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.VALIDATION_ERROR, "请求参数格式错误"));
    }

    /**
     * 路径变量/请求参数类型转换异常（如 Long 参数传入字符串、日期格式错误）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        log.warn("Type mismatch on {} {}: parameter '{}' failed to convert '{}' to {}",
                request.getMethod(), request.getRequestURI(),
                ex.getName(), ex.getValue(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        String message;
        if (ex.getRequiredType() != null && java.time.LocalDate.class.isAssignableFrom(ex.getRequiredType())) {
            message = String.format("参数 '%s' 日期格式无效，必须为 yyyy-MM-dd", ex.getName());
        } else {
            message = "请求参数格式错误";
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.VALIDATION_ERROR, message));
    }

    /**
     * 缺少必需请求参数（如 @RequestParam 标记的参数未传递）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParameterException(MissingServletRequestParameterException ex,
                                                                    HttpServletRequest request) {
        log.warn("Missing required parameter on {} {}: '{}'",
                request.getMethod(), request.getRequestURI(), ex.getParameterName());
        String message = String.format("缺少必需参数: %s", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST, message));
    }

    /**
     * HTTP 方法不支持（如对只支持 GET 的路径发 POST）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest request) {
        log.warn("Method not supported: {} {} (supported: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(40500, "不支持的请求方法: " + ex.getMethod()));
    }

    /**
     * 不支持的媒体类型（如需要 JSON 但传了 form-data）
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex,
                                                                         HttpServletRequest request) {
        log.warn("Unsupported media type on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(R.fail(41500, "不支持的内容类型: " + ex.getContentType()));
    }

    /**
     * 文件上传超出大小限制（Spring multipart 配置的 max-file-size）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                         HttpServletRequest request) {
        log.warn("File upload size exceeded on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST, "文件大小超出服务器限制（最大 50MB）"));
    }

    /**
     * 数据库唯一约束违规（并发竞态导致的重复插入）
     * <p>
     * 按约束名映射为对应的业务错误码，返回 409 Conflict 而非 500。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<R<Void>> handleDuplicateKeyException(DuplicateKeyException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        log.warn("Duplicate key violation on {} {}: {}", request.getMethod(), request.getRequestURI(), message);

        ErrorCode errorCode = resolveErrorCodeFromConstraint(message);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(errorCode));
    }

    /**
     * 约束名 → ErrorCode 映射表
     */
    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_MAP = Map.of(
            "project_key_key", ErrorCode.PROJECT_KEY_DUPLICATE,
            "organization_code_key", ErrorCode.ORG_CODE_DUPLICATE,
            "sys_role_code_key", ErrorCode.ROLE_CODE_DUPLICATE,
            "issue_tag_project_id_name_key", ErrorCode.DUPLICATE_RESOURCE,
            "issue_link_source_issue_id_target_issue_id_link_type_key", ErrorCode.DUPLICATE_RESOURCE,
            "issue_tag_relation_issue_id_tag_id_key", ErrorCode.DUPLICATE_RESOURCE,
            "project_member_project_user_role_key", ErrorCode.DUPLICATE_RESOURCE,
            "sys_user_username_key", ErrorCode.DUPLICATE_RESOURCE,
            "issue_status_code_key", ErrorCode.DUPLICATE_RESOURCE
    );

    private ErrorCode resolveErrorCodeFromConstraint(String exceptionMessage) {
        if (exceptionMessage != null) {
            for (Map.Entry<String, ErrorCode> entry : CONSTRAINT_ERROR_MAP.entrySet()) {
                if (exceptionMessage.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        // 未知约束名 → 使用通用重复资源错误码
        return ErrorCode.DUPLICATE_RESOURCE;
    }

    /**
     * 非预期异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }
}

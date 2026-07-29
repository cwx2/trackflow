package com.trackflow.common.exception;

import com.trackflow.auth.security.TrackFlowPermissionEvaluator;
import com.trackflow.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 所有异常统一转为 R 响应
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TrackFlowPermissionEvaluator permissionEvaluator;

    /**
     * 支持的 @PreAuthorize 表达式模式（用于早期权限检查）
     */
    private static final Pattern CHECK_ISSUE_PATTERN = Pattern.compile(
            "@perm\\.checkIssue\\(#(\\w+),\\s*'([^']+)'\\)");
    private static final Pattern CHECK_PROJECT_PATTERN = Pattern.compile(
            "@perm\\.checkProject\\(#(\\w+),\\s*'([^']+)'\\)");
    private static final Pattern CHECK_PATTERN = Pattern.compile(
            "@perm\\.check\\(#(\\w+),\\s*'([^']+)'\\)");
    private static final Pattern CHECK_SPRINT_PATTERN = Pattern.compile(
            "@perm\\.checkSprint\\(#(\\w+),\\s*'([^']+)'\\)");
    private static final Pattern CHECK_GLOBAL_PATTERN = Pattern.compile(
            "@perm\\.checkGlobal\\('([^']+)'\\)");
    private static final Pattern CHECK_DELETED_ISSUE_PATTERN = Pattern.compile(
            "@perm\\.checkDeletedIssue\\(#(\\w+),\\s*'([^']+)'\\)");

    /**
     * 参数校验异常
     * <p>
     * 安全优先原则：当参数校验失败时，先检查当前用户是否有权限执行该操作。
     * 如果权限不足，返回 403（不暴露接口参数结构）；否则返回 400 和具体错误。
     * <p>
     * 参考：https://github.com/spring-projects/spring-boot/issues/10157
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidationException(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        // 安全优先：先检查权限，权限不足则返回 403
        HandlerMethod handlerMethod = (HandlerMethod) request.getAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        
        if (handlerMethod != null) {
            boolean hasPermission = checkEarlyPermission(request, handlerMethod);
            if (!hasPermission) {
                log.info("Validation failed but user has no permission, returning 403 instead of 400: {} {}",
                        request.getMethod(), request.getRequestURI());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(R.fail(ErrorCode.ACCESS_DENIED, "权限不足，您没有执行此操作的权限"));
            }
        }

        // 权限检查通过，返回正常的参数校验错误
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.VALIDATION_ERROR, message));
    }

    /**
     * 早期权限检查：在参数校验失败时检查用户是否有权限
     * 
     * @return true 如果有权限或无法确定（保守处理）；false 如果明确无权限
     */
    @SuppressWarnings("unchecked")
    private boolean checkEarlyPermission(HttpServletRequest request, HandlerMethod handlerMethod) {
        PreAuthorize preAuthorize = handlerMethod.getMethodAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            return true;
        }

        String expression = preAuthorize.value();
        if (expression == null || expression.isBlank()) {
            return true;
        }

        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        try {
            return evaluatePermissionExpression(expression, pathVariables);
        } catch (Exception e) {
            log.debug("Failed to evaluate early permission: {}", e.getMessage());
            return true; // 保守返回 true
        }
    }

    /**
     * 解析并执行权限表达式
     */
    private boolean evaluatePermissionExpression(String expression, Map<String, String> pathVariables) {
        // 1. checkIssue(#id, 'permission')
        Matcher matcher = CHECK_ISSUE_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String permission = matcher.group(2);
            Long issueId = extractLongVariable(pathVariables, varName);
            if (issueId == null) return true;
            return permissionEvaluator.checkIssue(issueId, permission);
        }

        // 2. checkProject(#identifier, 'permission')
        matcher = CHECK_PROJECT_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String permission = matcher.group(2);
            String identifier = pathVariables != null ? pathVariables.get(varName) : null;
            if (identifier == null || identifier.isBlank()) return true;
            return permissionEvaluator.checkProject(identifier, permission);
        }

        // 3. check(#projectId, 'permission')
        matcher = CHECK_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String permission = matcher.group(2);
            Long projectId = extractLongVariable(pathVariables, varName);
            if (projectId == null) return true;
            return permissionEvaluator.check(projectId, permission);
        }

        // 4. checkSprint(#id, 'permission')
        matcher = CHECK_SPRINT_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String permission = matcher.group(2);
            Long sprintId = extractLongVariable(pathVariables, varName);
            if (sprintId == null) return true;
            return permissionEvaluator.checkSprint(sprintId, permission);
        }

        // 5. checkGlobal('permission')
        matcher = CHECK_GLOBAL_PATTERN.matcher(expression);
        if (matcher.find()) {
            String permission = matcher.group(1);
            return permissionEvaluator.checkGlobal(permission);
        }

        // 6. checkDeletedIssue(#id, 'permission')
        matcher = CHECK_DELETED_ISSUE_PATTERN.matcher(expression);
        if (matcher.find()) {
            String varName = matcher.group(1);
            String permission = matcher.group(2);
            Long issueId = extractLongVariable(pathVariables, varName);
            if (issueId == null) return true;
            return permissionEvaluator.checkDeletedIssue(issueId, permission);
        }

        // 无法解析的表达式，保守返回 true
        return true;
    }

    private Long extractLongVariable(Map<String, String> pathVariables, String varName) {
        if (pathVariables == null) return null;
        String value = pathVariables.get(varName);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
     * 数据库外键/完整性约束违规（如引用不存在的关联资源）
     * <p>
     * 返回 400 Bad Request + 明确错误信息，而非 500 内部错误。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex,
                                                                          HttpServletRequest request) {
        String message = ex.getMessage();
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(), message);

        String userMessage = "请求数据不合法，关联的资源可能不存在";
        if (message != null && message.contains("foreign key constraint")) {
            userMessage = "关联的资源不存在，请检查参数是否正确";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST, userMessage));
    }

    /**
     * 约束名 → ErrorCode 映射表
     */
    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_MAP = Map.of(
            "project_key_key", ErrorCode.PROJECT_KEY_DUPLICATE,
            "idx_project_name_unique", ErrorCode.PROJECT_NAME_DUPLICATE,
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

package com.trackflow.auth.security;

import com.trackflow.common.exception.EarlyPermissionChecker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 早期权限检查实现 — 在参数校验失败时提前检查 @PreAuthorize 权限
 * <p>
 * 安全优先原则：当参数校验失败时，先检查用户是否有权限执行该操作。
 * 如果权限不足，返回 403（不暴露接口参数结构）；否则返回 400 和具体错误。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEarlyPermissionChecker implements EarlyPermissionChecker {

    private final TrackFlowPermissionEvaluator permissionEvaluator;

    /**
     * 支持的 @PreAuthorize 表达式模式
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

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasPermission(HttpServletRequest request, HandlerMethod handlerMethod) {
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
}

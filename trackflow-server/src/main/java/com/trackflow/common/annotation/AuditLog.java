package com.trackflow.common.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解——标记需要自动记录审计日志的方法。
 * <p>
 * 切面自动捕获当前用户（ID/用户名/IP）、方法参数和执行结果，
 * 异步写入审计日志表，不影响主业务性能。
 * <p>
 * 用法示例：
 * <pre>{@code
 * @AuditLog(action = "USER_DISABLE", targetType = "user", targetId = "#userId")
 * public void disableUser(Long userId, DisableUserDTO dto) { ... }
 *
 * @AuditLog(action = "ROLE_ASSIGN", targetType = "role", targetId = "#dto.roleId")
 * public void assignRole(AssignRoleDTO dto) { ... }
 * }</pre>
 *
 * @author TrackFlow
 * @since 1.0
 * @see com.trackflow.common.aspect.AuditLogAdvisor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作类型，如 "disable_user"、"assign_global_role"、"create_project"。
     */
    String action();

    /**
     * 目标资源类型，如 "user"、"project"、"issue"。
     */
    String targetType();

    /**
     * 目标资源 ID，支持 SpEL 表达式。
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code "#userId"} — 方法参数 userId</li>
     *   <li>{@code "#dto.projectId"} — DTO 中的字段</li>
     *   <li>{@code "#result?.id"} — 返回值中的 ID（result 为方法返回值）</li>
     * </ul>
     * 为空时不记录 targetId。
     */
    String targetId() default "";

    /**
     * 操作描述（可选，支持 SpEL），如 "禁用用户 #{#user.displayName}"。
     */
    String description() default "";

    /**
     * 是否记录方法参数到 details 中，默认 false。
     * <p>
     * 设为 true 时，切面会将所有参数序列化为 JSON 存入 details 字段。
     * 注意：包含敏感信息（如密码）的方法应保持 false。
     */
    boolean logParams() default false;

    /**
     * 自定义 details 表达式（SpEL），用于精确控制记录哪些信息。
     * <p>
     * 表达式应返回 Map&lt;String, Object&gt; 或可 JSON 序列化对象。
     * 为空时根据 logParams 决定是否记录参数。
     * <p>
     * 示例：{@code "{'username': #user.username, 'roleName': #roleName}"}
     */
    String details() default "";
}

package com.trackflow.auth.security;

import java.lang.annotation.*;

/**
 * 标记 Controller 端点无需业务权限校验（但仍需认证）。
 * <p>
 * 用途：当端点仅需 JWT/API Key 认证即可访问时（如 /auth/me、文件下载），
 * 在方法或类上添加此注解以明确告知 {@link SecurityAnnotationVerifier} 该端点已审视过。
 * <p>
 * 对比 OpenProject 的 {@code no_authorization_required!} 机制：
 * 显式声明"此端点不需要业务权限检查"，而不是"忘记加了"。
 *
 * @see SecurityAnnotationVerifier
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAuthorizationRequired {

    /**
     * 说明为何此端点不需要业务权限检查（便于后续审计）
     */
    String reason() default "";
}

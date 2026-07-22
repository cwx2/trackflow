package com.trackflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.parameters.AnnotationParameterNameDiscoverer;
import org.springframework.security.core.parameters.P;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.PrioritizedParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 方法级安全配置 — 解决 @PreAuthorize SpEL 参数名解析问题。
 *
 * <p>问题背景：IDEA 编译时可能未应用 Maven 的 -parameters 标志，
 * 导致 Spring Security 的 {@code #paramName} SpEL 引用无法通过反射获取方法参数名。
 *
 * <p>解决方案：注册自定义 {@link PrioritizedParameterNameDiscoverer}，优先级链：
 * <ol>
 *   <li>{@link AnnotationParameterNameDiscoverer} — 从 @P / @PathVariable / @RequestParam 注解的 value 中提取参数名</li>
 *   <li>{@link DefaultParameterNameDiscoverer} — 标准 Spring 参数名发现（反射 + ASM 字节码）</li>
 * </ol>
 *
 * <p>这样 {@code @PreAuthorize("@perm.checkIssue(#id, ...)")} 中的 {@code #id}
 * 能通过 {@code @PathVariable("id")} 正确解析，无论是否启用 -parameters 编译标志。
 *
 * <p>对于 {@code #dto.projectId} 等引用 @RequestBody 参数的场景，
 * 使用 Spring Security 的 {@code @P("dto")} 注解显式声明参数名，
 * 由 {@link AnnotationParameterNameDiscoverer} 解析。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Configuration
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();

        PrioritizedParameterNameDiscoverer discoverer = new PrioritizedParameterNameDiscoverer();

        // 优先级 1：从注解的 value 属性中提取参数名
        // - @P("dto") — 解决 @RequestBody DTO 参数的 SpEL 引用（如 #dto.projectId）
        // - @PathVariable("id") — 解决路径参数的 SpEL 引用（如 #id）
        // - @RequestParam("xxx") — 解决查询参数的 SpEL 引用
        discoverer.addDiscoverer(
                new AnnotationParameterNameDiscoverer(
                        P.class.getName(),
                        PathVariable.class.getName(),
                        RequestParam.class.getName()
                )
        );

        // 优先级 2：标准 Spring 参数名发现
        // 包含 StandardReflectionParameterNameDiscoverer（需 -parameters 标志）
        // 以及 LocalVariableTableParameterNameDiscoverer（通过字节码分析，无需 -parameters）
        discoverer.addDiscoverer(new DefaultParameterNameDiscoverer());

        handler.setParameterNameDiscoverer(discoverer);
        return handler;
    }
}

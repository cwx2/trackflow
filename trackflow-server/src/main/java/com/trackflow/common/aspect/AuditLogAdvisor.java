package com.trackflow.common.aspect;

import com.trackflow.common.annotation.AuditLog;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.common.util.SpELUtils;
import com.trackflow.common.util.WebUtils;
import com.trackflow.system.service.SystemAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 审计日志 AOP 切面——拦截 {@code @AuditLog} 注解的方法，自动记录审计日志。
 * <p>
 * 使用 Spring 原生 Advisor API（无需 aspectjweaver 依赖），适配 Spring Boot 4.x。
 * <p>
 * ⚠️ 当前项目未引入 aspectjweaver / spring-boot-starter-aop，
 * Spring 原生 {@code DefaultAdvisorAutoProxyCreator} 与现有 Security/Transaction
 * 代理冲突。此 Advisor 暂时保留代码但<b>不注册为 Bean</b>（移除了 @Component）。
 * <p>
 * 激活方式（二选一）：
 * <ul>
 *   <li>在 pom.xml 中添加 spring-boot-starter-aop 并加 @EnableAspectJAutoProxy</li>
 *   <li>在需要审计的方法中直接调用 systemAuditService.log()（当前推荐方式）</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
// @Component — 暂未激活，需要 spring-boot-starter-aop 或 @EnableAspectJAutoProxy
public class AuditLogAdvisor extends AbstractPointcutAdvisor implements Ordered {

    private final Pointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(AuditLog.class);
    private final AuditLogInterceptor interceptor;

    public AuditLogAdvisor(@Lazy SystemAuditService auditService,
                           @Lazy @org.springframework.beans.factory.annotation.Qualifier("notificationExecutor") Executor executor) {
        this.interceptor = new AuditLogInterceptor(auditService, executor);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;  // Run after @Transactional and @PreAuthorize
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return interceptor;
    }

    /**
     * 方法拦截器——执行审计日志记录逻辑。
     */
    @Slf4j
    static class AuditLogInterceptor implements MethodInterceptor {

        private final SystemAuditService auditService;
        private final Executor asyncExecutor;

        AuditLogInterceptor(SystemAuditService auditService, Executor asyncExecutor) {
            this.auditService = auditService;
            this.asyncExecutor = asyncExecutor;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            AuditLog auditLog = method.getAnnotation(AuditLog.class);
            if (auditLog == null) {
                return invocation.proceed();
            }

            // 在主线程中捕获上下文信息（SecurityContext 和 RequestAttributes 线程绑定）
            Long userId = SecurityUtils.getCurrentUserId();
            String ipAddress = extractIpAddress();
            Object[] args = invocation.getArguments();

            Object result = null;
            String errorMsg = null;
            try {
                result = invocation.proceed();
                return result;
            } catch (Throwable e) {
                errorMsg = e.getMessage();
                throw e;
            } finally {
                // 异步写日志，不阻塞主业务
                Object finalResult = result;
                String finalError = errorMsg;
                CompletableFuture.runAsync(() ->
                        writeAuditLog(method, args, auditLog, userId, ipAddress, finalResult, finalError),
                        asyncExecutor
                );
            }
        }

        private void writeAuditLog(Method method, Object[] args, AuditLog auditLog,
                                   Long userId, String ipAddress, Object result, String errorMsg) {
            try {
                // 解析 targetId（SpEL）
                String targetIdStr = SpELUtils.parseToString(auditLog.targetId(), method, args, result);
                Long targetId = null;
                if (targetIdStr != null && !targetIdStr.isBlank()) {
                    try {
                        targetId = Long.parseLong(targetIdStr);
                    } catch (NumberFormatException e) {
                        log.debug("[AuditLog] targetId 非数字，忽略: {}", targetIdStr);
                    }
                }

                // 构建 details
                Map<String, Object> details = buildDetails(method, args, auditLog, result);
                if (errorMsg != null) {
                    details.put("_error", errorMsg);
                    details.put("_success", false);
                } else {
                    details.put("_success", true);
                }

                // 解析 description
                String description = SpELUtils.parseToString(auditLog.description(), method, args, result);
                if (description != null && !description.isBlank()) {
                    details.put("_description", description);
                }

                auditService.log(auditLog.action(), auditLog.targetType(), targetId, details);
            } catch (Exception e) {
                log.warn("[AuditLog] 审计日志写入失败: action={}, error={}", auditLog.action(), e.getMessage());
            }
        }

        /**
         * 构建审计详情 Map。
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> buildDetails(Method method, Object[] args, AuditLog auditLog, Object result) {
            if (!auditLog.details().isBlank()) {
                Object parsed = SpELUtils.parse(auditLog.details(), method, args, result);
                if (parsed instanceof Map) {
                    return new HashMap<>((Map<String, Object>) parsed);
                }
            }

            if (auditLog.logParams()) {
                return buildParamMap(method, args);
            }

            return new HashMap<>();
        }

        private Map<String, Object> buildParamMap(Method method, Object[] args) {
            Map<String, Object> params = new HashMap<>();
            var paramNames = new org.springframework.core.DefaultParameterNameDiscoverer()
                    .getParameterNames(method);
            if (paramNames == null || args == null) {
                return params;
            }
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                }
                params.put(paramNames[i], arg);
            }
            return params;
        }

        private String extractIpAddress() {
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    return WebUtils.getClientIp(attrs.getRequest());
                }
            } catch (Exception e) {
                // 非 Web 环境（如定时任务）
            }
            return null;
        }
    }
}

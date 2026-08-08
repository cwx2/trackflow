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
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 审计日志 AOP 切面——拦截 {@code @AuditLog} 注解的方法，自动记录审计日志。
 * <p>
 * 使用 Spring 原生 Advisor API（无需 aspectjweaver 依赖），适配 Spring Boot 4.x。
 * <p>
 * 特性：
 * <ul>
 *   <li>异步写入审计日志，不阻塞主业务</li>
 *   <li>主业务抛异常时仍记录（记录 error 信息）</li>
 *   <li>切面执行失败只打 WARN 日志，不影响主业务</li>
 *   <li>支持 SpEL 解析 targetId 和 details</li>
 * </ul>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class AuditLogAdvisor extends AbstractPointcutAdvisor {

    private final Pointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(AuditLog.class);
    private final AuditLogInterceptor interceptor;

    public AuditLogAdvisor(SystemAuditService auditService,
                           @org.springframework.beans.factory.annotation.Qualifier("notificationExecutor") Executor notificationExecutor) {
        this.interceptor = new AuditLogInterceptor(auditService, notificationExecutor);
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
         * <p>
         * 优先级：details SpEL > logParams > 空 Map。
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> buildDetails(Method method, Object[] args, AuditLog auditLog, Object result) {
            // 1. 尝试 details SpEL 表达式
            if (!auditLog.details().isBlank()) {
                Object parsed = SpELUtils.parse(auditLog.details(), method, args, result);
                if (parsed instanceof Map) {
                    return new HashMap<>((Map<String, Object>) parsed);
                }
            }

            // 2. logParams=true 时，记录参数名=值
            if (auditLog.logParams()) {
                return buildParamMap(method, args);
            }

            return new HashMap<>();
        }

        /**
         * 将方法参数构建为 name->value Map。
         * 跳过 HttpServletRequest 等框架对象。
         */
        private Map<String, Object> buildParamMap(Method method, Object[] args) {
            Map<String, Object> params = new HashMap<>();
            var paramNames = new org.springframework.core.DefaultParameterNameDiscoverer()
                    .getParameterNames(method);
            if (paramNames == null || args == null) {
                return params;
            }
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                Object arg = args[i];
                // 跳过框架对象
                if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                }
                params.put(paramNames[i], arg);
            }
            return params;
        }

        /**
         * 从当前 HTTP 请求中提取客户端 IP。
         */
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

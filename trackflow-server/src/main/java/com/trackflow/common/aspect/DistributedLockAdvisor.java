package com.trackflow.common.aspect;

import com.trackflow.common.annotation.DistributedLock;
import com.trackflow.common.annotation.LockFailPolicy;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁 AOP 切面——拦截 @DistributedLock 注解的方法，自动获取/释放 PostgreSQL Advisory Lock。
 * <p>
 * 使用 Spring 原生 Advisor API（无需 aspectjweaver 依赖），适配 Spring Boot 4.x。
 * <p>
 * 替代手写的 distributedLockService.executeWithLock() 模板代码，确保：
 * 1. 锁一定释放（finally 语义由 DistributedLockService 保证）
 * 2. lock key 由注解集中管理，避免字符串分散
 * 3. 失败策略可配置（SKIP / THROW）
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Component
public class DistributedLockAdvisor extends AbstractPointcutAdvisor {

    private final Pointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(DistributedLock.class);
    private final DistributedLockInterceptor interceptor;

    public DistributedLockAdvisor(DistributedLockService distributedLockService) {
        this.interceptor = new DistributedLockInterceptor(distributedLockService);
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
     * 方法拦截器——执行分布式锁逻辑。
     */
    @Slf4j
    @RequiredArgsConstructor
    static class DistributedLockInterceptor implements MethodInterceptor {

        private final DistributedLockService distributedLockService;
        private static final ExpressionParser PARSER = new SpelExpressionParser();
        private static final ParameterNameDiscoverer PARAM_DISCOVERER = new DefaultParameterNameDiscoverer();

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();
            DistributedLock lock = method.getAnnotation(DistributedLock.class);
            if (lock == null) {
                return invocation.proceed();
            }

            String key = resolveKey(invocation, lock.key());

            // 使用 executeWithLock 包装方法执行
            Object[] resultHolder = new Object[1];
            Throwable[] exHolder = new Throwable[1];

            boolean acquired = distributedLockService.executeWithLock(key, () -> {
                try {
                    resultHolder[0] = invocation.proceed();
                } catch (Throwable t) {
                    exHolder[0] = t;
                }
            });

            if (!acquired) {
                // 未获取到锁
                if (lock.onFail() == LockFailPolicy.THROW) {
                    throw new BusinessException(ErrorCode.CONCURRENT_OPERATION,
                            "操作正在进行中，请稍后重试 [lock=" + key + "]");
                }
                log.debug("[DistributedLock] 跳过执行，锁被占用: {}", key);
                return null;
            }

            // 锁获取成功，检查方法执行是否抛了异常
            if (exHolder[0] != null) {
                throw exHolder[0];
            }
            return resultHolder[0];
        }

        /**
         * 解析锁 key。
         * <p>
         * - 空字符串：自动使用 "类简名:方法名"
         * - 含 # 号：SpEL 表达式（如 "#projectId"）
         * - 其他：字面字符串
         */
        private String resolveKey(MethodInvocation invocation, String keyExpr) {
            if (keyExpr == null || keyExpr.isBlank()) {
                return invocation.getMethod().getDeclaringClass().getSimpleName()
                        + ":" + invocation.getMethod().getName();
            }

            if (keyExpr.contains("#")) {
                return evaluateSpEL(keyExpr, invocation);
            }

            return keyExpr;
        }

        /**
         * SpEL 表达式解析——支持方法参数引用（如 "#dto.projectId"、"#sprintId"）。
         */
        private String evaluateSpEL(String expression, MethodInvocation invocation) {
            try {
                Method method = invocation.getMethod();
                Object[] args = invocation.getArguments();

                StandardEvaluationContext context = new StandardEvaluationContext();
                String[] paramNames = PARAM_DISCOVERER.getParameterNames(method);
                if (paramNames != null) {
                    for (int i = 0; i < paramNames.length; i++) {
                        context.setVariable(paramNames[i], args[i]);
                    }
                }

                Object result = PARSER.parseExpression(expression).getValue(context);
                return result != null ? result.toString() : "null";
            } catch (Exception e) {
                log.warn("[DistributedLock] SpEL 解析失败: expression='{}', error={}", expression, e.getMessage());
                // 降级为字面字符串，避免锁失效
                return expression;
            }
        }
    }
}

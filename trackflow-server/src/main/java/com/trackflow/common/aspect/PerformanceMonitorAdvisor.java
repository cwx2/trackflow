package com.trackflow.common.aspect;

import com.trackflow.common.annotation.SlowLog;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 慢操作性能监控 AOP 切面——自动对所有 {@code @Service} 公开方法计时，超过阈值打 WARN 日志。
 * <p>
 * 使用 Spring 原生 Advisor API（无需 aspectjweaver 依赖），适配 Spring Boot 4.x。
 * <p>
 * 双层监控策略：
 * <ol>
 *   <li><b>全局切面</b>（无需注解）：对所有 @Service 公开方法自动计时，
 *       超过 {@code trackflow.monitor.slow-threshold}（默认 500ms）打 WARN</li>
 *   <li><b>{@link SlowLog} 注解</b>（精细控制）：覆盖阈值、强制记录、或排除监控</li>
 * </ol>
 * <p>
 * 性能开销极低（一次 System.currentTimeMillis 调用 + 一次减法），不影响正常业务。
 *
 * @author TrackFlow
 * @since 1.0
 * @see SlowLog
 */
@Slf4j
@Component
public class PerformanceMonitorAdvisor extends AbstractPointcutAdvisor {

    private final Pointcut pointcut = new ServicePublicMethodPointcut();
    private final PerformanceMonitorInterceptor interceptor;

    public PerformanceMonitorAdvisor(
            @Value("${trackflow.monitor.slow-threshold:500}") long defaultThreshold,
            @Value("${trackflow.monitor.enabled:true}") boolean enabled) {
        this.interceptor = new PerformanceMonitorInterceptor(defaultThreshold, enabled);
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
     * 设置较低优先级，确保在业务切面（AuditLog、DistributedLock）之后执行，
     * 计时包含业务切面的开销更准确。
     */
    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 10;
    }

    // ============ Pointcut：匹配 @Service 类的 public 方法 ============

    /**
     * 切点定义：匹配标注了 @Service 的类中所有 public 方法。
     * <p>
     * 不匹配 Object 继承方法（toString/hashCode/equals）以减少噪音。
     */
    static class ServicePublicMethodPointcut extends StaticMethodMatcherPointcut {

        ServicePublicMethodPointcut() {
            setClassFilter(clazz -> clazz.isAnnotationPresent(Service.class));
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            // 只匹配 public 方法
            if (!Modifier.isPublic(method.getModifiers())) {
                return false;
            }
            // 排除 Object 继承方法
            if (method.getDeclaringClass() == Object.class) {
                return false;
            }
            // 排除 Spring 生命周期方法
            String name = method.getName();
            return !"afterPropertiesSet".equals(name) && !"destroy".equals(name);
        }
    }

    // ============ Interceptor：执行计时和日志输出 ============

    @Slf4j
    static class PerformanceMonitorInterceptor implements MethodInterceptor {

        private final long defaultThreshold;
        private final boolean enabled;

        /** 缓存方法注解解析结果，避免每次反射 */
        private final Map<Method, SlowLogConfig> configCache = new ConcurrentHashMap<>();

        PerformanceMonitorInterceptor(long defaultThreshold, boolean enabled) {
            this.defaultThreshold = defaultThreshold;
            this.enabled = enabled;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if (!enabled) {
                return invocation.proceed();
            }

            Method method = invocation.getMethod();
            SlowLogConfig config = configCache.computeIfAbsent(method, this::resolveConfig);

            // 排除的方法直接放行
            if (config.excluded) {
                return invocation.proceed();
            }

            long start = System.currentTimeMillis();
            try {
                return invocation.proceed();
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                emitLog(invocation, config, elapsed);
            }
        }

        private void emitLog(MethodInvocation invocation, SlowLogConfig config, long elapsed) {
            String className = invocation.getMethod().getDeclaringClass().getSimpleName();
            String methodName = invocation.getMethod().getName();

            if (config.alwaysLog) {
                log.info("[Perf] {}.{}() 耗时 {}ms", className, methodName, elapsed);
            } else if (elapsed > config.threshold) {
                log.warn("[SlowOp] {}.{}() 耗时 {}ms，超过阈值 {}ms",
                        className, methodName, elapsed, config.threshold);
            }
        }

        private SlowLogConfig resolveConfig(Method method) {
            SlowLog annotation = method.getAnnotation(SlowLog.class);
            if (annotation == null) {
                // 无注解：使用全局默认值
                return new SlowLogConfig(defaultThreshold, false, false);
            }
            if (annotation.exclude()) {
                return new SlowLogConfig(0, false, true);
            }
            long threshold = annotation.threshold() > 0 ? annotation.threshold() : defaultThreshold;
            return new SlowLogConfig(threshold, annotation.alwaysLog(), false);
        }
    }

    /**
     * 缓存的方法监控配置，避免每次调用都解析注解。
     */
    record SlowLogConfig(long threshold, boolean alwaysLog, boolean excluded) {
    }
}

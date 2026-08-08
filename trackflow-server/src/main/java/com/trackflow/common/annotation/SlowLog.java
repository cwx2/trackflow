package com.trackflow.common.annotation;

import java.lang.annotation.*;

/**
 * 慢操作监控注解——标记需要精细控制性能监控的方法。
 * <p>
 * 默认情况下，{@link com.trackflow.common.aspect.PerformanceMonitorAdvisor} 会自动监控
 * 所有 {@code @Service} 公开方法，使用全局阈值（{@code trackflow.monitor.slow-threshold}，默认 500ms）。
 * <p>
 * 本注解用于：
 * <ul>
 *   <li>覆盖全局阈值（如数据迁移方法允许更长耗时）</li>
 *   <li>强制记录（无论耗时长短，用于关键业务方法）</li>
 *   <li>排除监控（对高频简单方法避免日志噪音）</li>
 * </ul>
 * <p>
 * 用法示例：
 * <pre>{@code
 * // 覆盖阈值：允许最多 2000ms
 * @SlowLog(threshold = 2000)
 * public void importData(ImportDTO dto) { ... }
 *
 * // 强制记录：无论耗时都 INFO 输出
 * @SlowLog(alwaysLog = true)
 * public void completeSprintAndMoveIssues(Long sprintId) { ... }
 *
 * // 排除监控：此方法不需要性能追踪
 * @SlowLog(exclude = true)
 * public CacheEntry getCachedValue(String key) { ... }
 * }</pre>
 *
 * @author TrackFlow
 * @since 1.0
 * @see com.trackflow.common.aspect.PerformanceMonitorAdvisor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlowLog {

    /**
     * 慢操作阈值（毫秒），超过此值打 WARN 日志。
     * <p>
     * 默认 -1 表示使用全局配置值（{@code trackflow.monitor.slow-threshold}）。
     * 设为正数时覆盖全局阈值。
     */
    long threshold() default -1;

    /**
     * 是否强制记录（无论耗时长短）。
     * <p>
     * 设为 true 时，方法每次执行都会输出 INFO 级别日志（含耗时），
     * 用于关键业务操作的执行追踪。
     */
    boolean alwaysLog() default false;

    /**
     * 是否排除此方法的性能监控。
     * <p>
     * 设为 true 时，切面不会对此方法进行计时和日志输出。
     * 用于高频调用的简单方法（如缓存读取），避免日志噪音。
     */
    boolean exclude() default false;
}

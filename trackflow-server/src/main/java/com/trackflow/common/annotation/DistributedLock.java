package com.trackflow.common.annotation;

import java.lang.annotation.*;

/**
 * 分布式锁注解——标记需要集群单活执行的方法。
 * <p>
 * 底层使用 PostgreSQL Advisory Lock，保证同一时刻只有一个实例执行该方法。
 * 适用于定时任务（@Scheduled）等需要防止重复执行的场景。
 * <p>
 * 用法示例：
 * <pre>{@code
 * @DistributedLock(key = "scheduler:notification-retry")
 * @Scheduled(fixedDelay = 120_000)
 * public void retryPendingNotifications() {
 *     // 业务逻辑，无需手写 lock/unlock
 * }
 * }</pre>
 *
 * @author TrackFlow
 * @since 1.0
 * @see com.trackflow.common.aspect.DistributedLockAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁 key（PostgreSQL Advisory Lock 的 lock name）。
     * <p>
     * 不填时自动使用 "类简名:方法名" 作为 key。
     * 支持 SpEL 表达式（以 # 开头，如 "#dto.projectId"），
     * 用于需要按资源维度加锁的场景。
     */
    String key() default "";

    /**
     * 获取锁失败时的行为策略。
     * <p>
     * - SKIP：静默跳过，方法不执行，返回 null（适用于定时任务）
     * - THROW：抛出 BusinessException（适用于用户操作需要反馈的场景）
     */
    LockFailPolicy onFail() default LockFailPolicy.SKIP;
}

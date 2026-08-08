package com.trackflow.common.annotation;

/**
 * 获取分布式锁失败时的处理策略。
 *
 * @author TrackFlow
 * @since 1.0
 */
public enum LockFailPolicy {

    /**
     * 跳过执行：方法不执行，不抛异常，仅打 DEBUG 日志。
     * 适用于定时任务——另一实例正在处理时静默跳过即可。
     */
    SKIP,

    /**
     * 抛出异常：抛出 BusinessException(CONCURRENT_OPERATION)。
     * 适用于用户触发的操作——需要告知用户"操作正在进行中，请稍后重试"。
     */
    THROW
}

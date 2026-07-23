package com.trackflow.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 分布式锁服务 — 基于 PostgreSQL Advisory Lock 实现。
 * <p>
 * 用于定时任务等需要保证多实例部署时仅单节点执行的场景。
 * Advisory Lock 是会话级锁，不写入任何表，零额外依赖。
 * <p>
 * 使用方式：
 * <pre>{@code
 * distributedLockService.executeWithLock("sprint_auto_complete", () -> {
 *     // 业务逻辑
 * });
 * }</pre>
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 尝试获取 PostgreSQL Advisory Lock 并执行任务。
     * <p>
     * 如果锁已被其他实例持有，立即返回 false（非阻塞）。
     * 任务执行完毕后自动释放锁。
     *
     * @param lockName 锁名称（会通过 hashtext 转为 int4 作为锁 ID）
     * @param task     需要执行的任务
     * @return true=获取锁并执行成功，false=未获取到锁（另一实例正在执行）
     */
    public boolean executeWithLock(String lockName, Runnable task) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(hashtext(?))", Boolean.class, lockName);

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[DistributedLock] 未获取到锁 '{}', 另一实例正在处理，跳过", lockName);
            return false;
        }

        try {
            task.run();
            return true;
        } finally {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(hashtext(?))", Boolean.class, lockName);
        }
    }

    /**
     * 尝试获取锁并执行有返回值的任务。
     *
     * @param lockName 锁名称
     * @param task     需要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果，如果未获取到锁返回 null
     */
    public <T> T executeWithLock(String lockName, Supplier<T> task) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(hashtext(?))", Boolean.class, lockName);

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[DistributedLock] 未获取到锁 '{}', 另一实例正在处理，跳过", lockName);
            return null;
        }

        try {
            return task.get();
        } finally {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(hashtext(?))", Boolean.class, lockName);
        }
    }
}

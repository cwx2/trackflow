package com.trackflow.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 请求频率限制服务
 * <p>
 * 使用 Redis 滑动窗口计数器实现多层限流策略：
 * <ul>
 *   <li>认证失败限制：同一 IP 在 5 分钟窗口内最多 10 次认证失败</li>
 *   <li>全局 API 限制：同一 IP 每分钟最多 200 次请求</li>
 * </ul>
 * <p>
 * 参考 OpenProject 的 Rack::Attack 限流设计（Login: burst 20/min, ban 30min）。
 * 本实现采用更保守的阈值以保护 API Key 暴力破解风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    // ====== 认证失败限流配置 ======
    /** 认证失败最大允许次数 */
    private static final int AUTH_FAILURE_MAX_ATTEMPTS = 10;
    /** 认证失败计数窗口（秒） */
    private static final long AUTH_FAILURE_WINDOW_SECONDS = 300; // 5 分钟
    /** 触发限流后的封禁时长（秒） */
    private static final long AUTH_BAN_DURATION_SECONDS = 900; // 15 分钟

    // ====== 全局 API 限流配置 ======
    /** 全局 API 每分钟最大请求数 */
    private static final int GLOBAL_API_MAX_REQUESTS = 200;
    /** 全局 API 计数窗口（秒） */
    private static final long GLOBAL_API_WINDOW_SECONDS = 60; // 1 分钟

    // ====== Redis Key 前缀 ======
    private static final String KEY_AUTH_FAILURE = "ratelimit:auth_fail:";
    private static final String KEY_AUTH_BAN = "ratelimit:auth_ban:";
    private static final String KEY_GLOBAL_API = "ratelimit:api:";

    /**
     * 检查某 IP 是否已被封禁（认证失败过多）。
     *
     * @param clientIp 客户端 IP
     * @return true 表示已被封禁，应直接返回 429
     */
    public boolean isAuthBanned(String clientIp) {
        String banKey = KEY_AUTH_BAN + clientIp;
        return Boolean.TRUE.equals(redisTemplate.hasKey(banKey));
    }

    /**
     * 记录一次认证失败并判断是否触发封禁。
     * <p>
     * 使用 Redis INCR + EXPIRE 实现简单窗口计数。当失败次数超过阈值时设置封禁标记。
     *
     * @param clientIp 客户端 IP
     * @return true 表示此次失败后触发了封禁
     */
    public boolean recordAuthFailure(String clientIp) {
        String failKey = KEY_AUTH_FAILURE + clientIp;

        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count == null) {
            return false;
        }

        // 首次写入时设置过期时间
        if (count == 1) {
            redisTemplate.expire(failKey, Duration.ofSeconds(AUTH_FAILURE_WINDOW_SECONDS));
        }

        // 检查是否超过阈值
        if (count >= AUTH_FAILURE_MAX_ATTEMPTS) {
            // 设置封禁标记
            String banKey = KEY_AUTH_BAN + clientIp;
            redisTemplate.opsForValue().set(banKey, String.valueOf(count),
                    Duration.ofSeconds(AUTH_BAN_DURATION_SECONDS));
            // 清除失败计数（已封禁，不再需要计数）
            redisTemplate.delete(failKey);

            log.warn("IP {} banned for {} seconds after {} auth failures",
                    clientIp, AUTH_BAN_DURATION_SECONDS, count);
            return true;
        }

        return false;
    }

    /**
     * 检查全局 API 频率限制。
     * <p>
     * 同一 IP 每分钟最多 {@link #GLOBAL_API_MAX_REQUESTS} 次请求。
     *
     * @param clientIp 客户端 IP
     * @return true 表示超过限制，应返回 429
     */
    public boolean isGlobalApiLimited(String clientIp) {
        String apiKey = KEY_GLOBAL_API + clientIp;

        Long count = redisTemplate.opsForValue().increment(apiKey);
        if (count == null) {
            return false;
        }

        // 首次写入时设置过期时间
        if (count == 1) {
            redisTemplate.expire(apiKey, Duration.ofSeconds(GLOBAL_API_WINDOW_SECONDS));
        }

        return count > GLOBAL_API_MAX_REQUESTS;
    }

    /**
     * 认证成功后清除该 IP 的失败计数（正常用户不应被误封）。
     *
     * @param clientIp 客户端 IP
     */
    public void clearAuthFailures(String clientIp) {
        String failKey = KEY_AUTH_FAILURE + clientIp;
        redisTemplate.delete(failKey);
    }

    /**
     * 获取封禁剩余时间（秒）。
     *
     * @param clientIp 客户端 IP
     * @return 剩余秒数，如果未被封禁返回 0
     */
    public long getBanRemainingSeconds(String clientIp) {
        String banKey = KEY_AUTH_BAN + clientIp;
        Long ttl = redisTemplate.getExpire(banKey);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    /**
     * 获取认证失败封禁时长配置（用于 Retry-After 响应头）。
     */
    public long getAuthBanDurationSeconds() {
        return AUTH_BAN_DURATION_SECONDS;
    }

    /**
     * 获取全局 API 限流窗口时长配置（用于 Retry-After 响应头）。
     */
    public long getGlobalApiWindowSeconds() {
        return GLOBAL_API_WINDOW_SECONDS;
    }
}

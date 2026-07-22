package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.integration.entity.NotificationOutbox;
import com.trackflow.integration.mapper.NotificationOutboxMapper;
import com.trackflow.integration.vo.NotificationOutboxVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知发件箱服务：管理通知发送失败后的持久化、重试和查询。
 * <p>
 * 重试策略（指数退避）：
 * - 第 1 次重试：1 分钟后
 * - 第 2 次重试：5 分钟后
 * - 第 3 次重试：30 分钟后
 * - 第 4 次重试：2 小时后
 * - 第 5 次重试：12 小时后
 * - 超过 5 次：标记为 failed，不再自动重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxMapper outboxMapper;

    /**
     * 指数退避延迟（分钟）：retry_count → delay
     */
    private static final int[] RETRY_DELAYS_MINUTES = {1, 5, 30, 120, 720};

    private static final int DEFAULT_MAX_RETRIES = 5;

    /**
     * 将失败的通知事件写入发件箱。
     *
     * @param eventType    事件类型（方法名，如 "notifyAssigned"）
     * @param payload      完整事件参数 JSON
     * @param errorMessage 异常信息
     */
    @Transactional(rollbackFor = Exception.class)
        public void saveFailedEvent(String eventType, String payload, String errorMessage) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setStatus(NotificationOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(DEFAULT_MAX_RETRIES);
        outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(RETRY_DELAYS_MINUTES[0]));
        outbox.setErrorMessage(truncateMessage(errorMessage));
        outbox.setCreatedAt(LocalDateTime.now());
        outboxMapper.insert(outbox);
        log.info("[NotificationOutbox] 保存失败事件: eventType={}, id={}", eventType, outbox.getId());
    }

    /**
     * 获取到期的待重试记录（定时任务调用）。
     *
     * @param limit 一次最多取多少条
     */
    public List<NotificationOutbox> fetchPendingForRetry(int limit) {
        return outboxMapper.selectPendingForRetry(LocalDateTime.now(), limit);
    }

    /**
     * 标记重试成功。
     */
    @Transactional(rollbackFor = Exception.class)
        public void markCompleted(Long id) {
        NotificationOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) return;
        outbox.setStatus(NotificationOutbox.STATUS_COMPLETED);
        outbox.setCompletedAt(LocalDateTime.now());
        outbox.setErrorMessage(null);
        outboxMapper.updateById(outbox);
        log.info("[NotificationOutbox] 重试成功: id={}, eventType={}", id, outbox.getEventType());
    }

    /**
     * 标记重试失败，递增 retry_count 并计算下次重试时间。
     * 若超过最大重试次数则标记为 failed。
     */
    @Transactional(rollbackFor = Exception.class)
        public void markRetryFailed(Long id, String errorMessage) {
        NotificationOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) return;

        int newRetryCount = outbox.getRetryCount() + 1;
        outbox.setRetryCount(newRetryCount);
        outbox.setErrorMessage(truncateMessage(errorMessage));

        if (newRetryCount >= outbox.getMaxRetries()) {
            // 超过最大重试次数，标记为 failed
            outbox.setStatus(NotificationOutbox.STATUS_FAILED);
            outbox.setNextRetryAt(null);
            log.warn("[NotificationOutbox] 超过最大重试次数，标记为失败: id={}, eventType={}, retryCount={}",
                    id, outbox.getEventType(), newRetryCount);
        } else {
            // 计算下次重试时间（指数退避）
            int delayMinutes = getDelayMinutes(newRetryCount);
            outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
            log.info("[NotificationOutbox] 重试失败，将在 {} 分钟后重试: id={}, retryCount={}",
                    delayMinutes, id, newRetryCount);
        }
        outboxMapper.updateById(outbox);
    }

    /**
     * 管理员手动重试：将 failed 记录重置为 pending。
     *
     * @return true 如果重置成功
     */
    @Transactional(rollbackFor = Exception.class)
        public boolean manualRetry(Long id) {
        NotificationOutbox outbox = outboxMapper.selectById(id);
        if (outbox == null) {
            return false;
        }
        if (!NotificationOutbox.STATUS_FAILED.equals(outbox.getStatus())) {
            return false;
        }
        outbox.setStatus(NotificationOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(DEFAULT_MAX_RETRIES);
        outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        outbox.setErrorMessage(null);
        outboxMapper.updateById(outbox);
        log.info("[NotificationOutbox] 管理员手动重试: id={}, eventType={}", id, outbox.getEventType());
        return true;
    }

    /**
     * 分页查询发件箱记录（管理员视图）。
     *
     * @param status 状态过滤（null 查所有）
     */
    public PageResult<NotificationOutboxVO> listOutbox(String status, int page, int pageSize) {
        LambdaQueryWrapper<NotificationOutbox> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(NotificationOutbox::getStatus, status);
        }
        wrapper.orderByDesc(NotificationOutbox::getCreatedAt);

        Page<NotificationOutbox> p = new Page<>(page, pageSize);
        Page<NotificationOutbox> result = outboxMapper.selectPage(p, wrapper);

        List<NotificationOutboxVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 获取发件箱统计（各状态数量）。
     */
    public OutboxStats getStats() {
        long pending = outboxMapper.selectCount(
                new LambdaQueryWrapper<NotificationOutbox>()
                        .eq(NotificationOutbox::getStatus, NotificationOutbox.STATUS_PENDING));
        long failed = outboxMapper.selectCount(
                new LambdaQueryWrapper<NotificationOutbox>()
                        .eq(NotificationOutbox::getStatus, NotificationOutbox.STATUS_FAILED));
        long completed = outboxMapper.selectCount(
                new LambdaQueryWrapper<NotificationOutbox>()
                        .eq(NotificationOutbox::getStatus, NotificationOutbox.STATUS_COMPLETED));
        return new OutboxStats(pending, failed, completed);
    }

    /**
     * 清理已完成的旧记录（7 天前的 completed 记录）。
     */
    @Transactional(rollbackFor = Exception.class)
        public int cleanupCompleted() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        return outboxMapper.cleanupCompleted(cutoff);
    }

    // ==================== 私有辅助方法 ====================

    private int getDelayMinutes(int retryCount) {
        if (retryCount < 0) return RETRY_DELAYS_MINUTES[0];
        if (retryCount >= RETRY_DELAYS_MINUTES.length) return RETRY_DELAYS_MINUTES[RETRY_DELAYS_MINUTES.length - 1];
        return RETRY_DELAYS_MINUTES[retryCount];
    }

    private String truncateMessage(String message) {
        if (message == null) return null;
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private NotificationOutboxVO toVO(NotificationOutbox entity) {
        NotificationOutboxVO vo = new NotificationOutboxVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setEventType(entity.getEventType());
        vo.setPayload(entity.getPayload());
        vo.setStatus(entity.getStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setMaxRetries(entity.getMaxRetries());
        vo.setNextRetryAt(entity.getNextRetryAt());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setCompletedAt(entity.getCompletedAt());
        return vo;
    }

    // ==================== 内部 DTO ====================

    public record OutboxStats(long pending, long failed, long completed) {}
}

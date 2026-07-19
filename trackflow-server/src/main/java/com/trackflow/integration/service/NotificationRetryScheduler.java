package com.trackflow.integration.service;

import com.trackflow.integration.entity.NotificationOutbox;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知重试定时任务：扫描 notification_outbox 中到期的 pending 记录并重新投递。
 * <p>
 * 执行频率：每 2 分钟扫描一次。
 * 每次最多处理 20 条记录，避免长时间占用线程。
 * <p>
 * 重试成功则标记为 completed，失败则递增 retry_count + 计算下次重试时间。
 * 超过最大重试次数后标记为 failed，等待管理员手动处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationOutboxService outboxService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 20;

    /**
     * 每 2 分钟扫描一次待重试记录
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void retryPendingNotifications() {
        List<NotificationOutbox> pending = outboxService.fetchPendingForRetry(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        log.info("[NotificationRetry] 开始重试 {} 条待处理通知", pending.size());
        int success = 0;
        int failed = 0;

        for (NotificationOutbox outbox : pending) {
            try {
                executeNotification(outbox);
                outboxService.markCompleted(outbox.getId());
                success++;
            } catch (Exception e) {
                outboxService.markRetryFailed(outbox.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("[NotificationRetry] 重试完成: success={}, failed={}", success, failed);
    }

    /**
     * 每天凌晨 3:00 清理 7 天前已完成的 outbox 记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupCompletedRecords() {
        int cleaned = outboxService.cleanupCompleted();
        if (cleaned > 0) {
            log.info("[NotificationRetry] 清理已完成的旧记录: {} 条", cleaned);
        }
    }

    /**
     * 根据 outbox 记录的 payload 重新执行通知投递。
     * <p>
     * payload JSON 格式：
     * {
     *   "method": "notify" | "notifyBatch",
     *   "userId": 123,          // notify 时使用
     *   "userIds": [123, 456],  // notifyBatch 时使用
     *   "actorId": 789,
     *   "title": "...",
     *   "content": "...",
     *   "type": "issue_assigned",
     *   "reason": "assigned",
     *   "resourceType": "issue",
     *   "resourceId": 101,
     *   "projectId": 1
     * }
     */
    private void executeNotification(NotificationOutbox outbox) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(outbox.getPayload(),
                new TypeReference<Map<String, Object>>() {});

        String method = (String) payload.get("method");
        String title = (String) payload.get("title");
        String content = (String) payload.get("content");
        String typeStr = (String) payload.get("type");
        String reasonStr = (String) payload.get("reason");
        String resourceType = (String) payload.get("resourceType");
        Long actorId = toLong(payload.get("actorId"));
        Long resourceId = toLong(payload.get("resourceId"));
        Long projectId = toLong(payload.get("projectId"));

        NotificationType type = NotificationType.valueOf(typeStr);
        NotificationReason reason = reasonStr != null ? NotificationReason.valueOf(reasonStr) : null;

        if ("notifyBatch".equals(method)) {
            @SuppressWarnings("unchecked")
            List<Number> userIdNumbers = (List<Number>) payload.get("userIds");
            Set<Long> userIds = userIdNumbers.stream()
                    .map(Number::longValue)
                    .collect(Collectors.toSet());
            notificationService.notifyBatch(userIds, actorId, title, content,
                    type, reason, resourceType, resourceId, projectId);
        } else {
            // 默认 "notify"
            Long userId = toLong(payload.get("userId"));
            notificationService.notify(userId, actorId, title, content,
                    type, reason, resourceType, resourceId, projectId);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

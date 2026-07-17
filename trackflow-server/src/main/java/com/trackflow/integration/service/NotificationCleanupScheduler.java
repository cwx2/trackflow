package com.trackflow.integration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知自动清理定时任务。
 * <p>
 * 每天凌晨 2:30 执行，清理超过保留期限的已读通知。
 * 保留天数通过配置 trackflow.notification.retention-days 指定，默认 90 天。
 * 设为 0 或负数表示永不自动清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    @Value("${trackflow.notification.retention-days:90}")
    private int retentionDays;

    /**
     * 每天凌晨 2:30 执行清理
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanExpiredNotifications() {
        if (retentionDays <= 0) {
            log.debug("[NotificationCleanup] 保留策略为永久保留，跳过清理");
            return;
        }

        log.info("[NotificationCleanup] 开始清理 {} 天前的已读通知...", retentionDays);
        try {
            int deleted = notificationService.cleanupExpiredNotifications(retentionDays);
            log.info("[NotificationCleanup] 清理完成，共删除 {} 条过期已读通知", deleted);
        } catch (Exception e) {
            log.error("[NotificationCleanup] 清理过程中发生异常", e);
        }
    }
}

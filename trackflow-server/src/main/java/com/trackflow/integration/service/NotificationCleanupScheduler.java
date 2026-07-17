package com.trackflow.integration.service;

import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知自动清理定时任务。
 * <p>
 * 每天凌晨 2:30 执行，清理超过保留期限的已读通知。
 * 保留天数从 system_setting 表动态读取（key: notification.retention_days），默认 90 天。
 * 设为 0 表示永不自动清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;

    /**
     * 每天凌晨 2:30 执行清理
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void cleanExpiredNotifications() {
        // 从 system_setting 读取动态配置的保留天数
        int retentionDays;
        try {
            retentionDays = Integer.parseInt(
                    systemSettingService.getSettingValue("notification.retention_days", "90"));
        } catch (NumberFormatException e) {
            retentionDays = 90;
        }

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

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
 * <p>
 * 同时清理 notification_muted_thread 表中引用已不存在资源的孤立记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;
    private final MutedThreadService mutedThreadService;
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
        } else {
            log.info("[NotificationCleanup] 开始清理 {} 天前的已读通知...", retentionDays);
            try {
                int deleted = notificationService.cleanupExpiredNotifications(retentionDays);
                log.info("[NotificationCleanup] 清理完成，共删除 {} 条过期已读通知", deleted);
            } catch (Exception e) {
                log.error("[NotificationCleanup] 清理过程中发生异常", e);
            }
        }

        // 清理孤立的 muted_thread 记录（引用的资源已不存在）
        try {
            int orphaned = mutedThreadService.cleanupOrphanedRecords();
            if (orphaned > 0) {
                log.info("[NotificationCleanup] 清理孤立静音记录 {} 条", orphaned);
            }
        } catch (Exception e) {
            log.error("[NotificationCleanup] 清理孤立静音记录时发生异常", e);
        }
    }
}

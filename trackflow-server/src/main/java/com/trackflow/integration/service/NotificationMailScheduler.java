package com.trackflow.integration.service;

import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知邮件延迟发送定时任务。
 * <p>
 * 参考 OpenProject WorkflowJob 的两阶段模型：
 * Phase 1（即时）：创建 IAN 站内通知，mention 类型立即发邮件
 * Phase 2（延迟）：聚合窗口结束后，检查 IAN 是否已读，未读则发送邮件
 * <p>
 * 核心逻辑：
 * 1. 先抑制已读通知（用户已在站内查看过 → 不再发邮件）
 * 2. 查找聚合窗口结束后仍未读且邮件未发的通知
 * 3. 按 (userId, resourceType, resourceId) 分组，同一资源的多条聚合通知合并为一封邮件
 * 4. 发送成功后标记 mail_sent=true
 * <p>
 * 执行频率：每 60 秒。确保聚合窗口结束后邮件在 1 分钟内发出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMailScheduler {

    private final NotificationService notificationService;
    private final EmailSendService emailSendService;
    private final NotificationPreferenceService preferenceService;
    private final SysUserMapper sysUserMapper;

    /**
     * 每批最多处理的通知数量（避免长时间占用线程）
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 每 60 秒执行一次，处理待发邮件的通知。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void processDelayedMails() {
        // Phase 1: 抑制已读通知（IAN 已读 → 不发邮件）
        int suppressed = notificationService.suppressReadNotificationMails();
        if (suppressed > 0) {
            log.debug("[NotificationMail] 抑制已读通知邮件: {} 条（用户已在站内查看）", suppressed);
        }

        // Phase 2: 查找聚合窗口结束后仍未读且邮件未发的通知
        List<Notification> pending = notificationService.findPendingMailNotifications(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        log.info("[NotificationMail] 开始处理延迟邮件: {} 条待发送", pending.size());

        // 收集所有涉及的 userId
        Set<Long> userIds = pending.stream()
                .map(Notification::getUserId)
                .collect(Collectors.toSet());

        // 批量查询用户信息
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        // 批量查询用户邮件偏好
        List<NotificationPreference> prefs = preferenceService.listGlobalByUserIds(userIds);
        Map<Long, NotificationPreference> prefMap = prefs.stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, p -> p, (a, b) -> a));

        // 按 (userId, resourceType, resourceId) 分组，合并同一资源的多条通知为一封邮件
        Map<MailGroupKey, List<Notification>> grouped = pending.stream()
                .collect(Collectors.groupingBy(n ->
                        new MailGroupKey(n.getUserId(), n.getResourceType(), n.getResourceId())));

        Set<Long> sentNotificationIds = new HashSet<>();
        int emailsSent = 0;
        int emailsSkipped = 0;

        for (Map.Entry<MailGroupKey, List<Notification>> entry : grouped.entrySet()) {
            MailGroupKey key = entry.getKey();
            List<Notification> notifications = entry.getValue();

            SysUser user = userMap.get(key.userId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                // 用户无邮箱，标记为已发送（跳过）
                sentNotificationIds.addAll(notifications.stream().map(Notification::getId).toList());
                emailsSkipped += notifications.size();
                continue;
            }

            // 检查用户邮件偏好
            NotificationPreference pref = prefMap.get(key.userId());
            if (pref != null && !Boolean.TRUE.equals(pref.getEmailEnabled())) {
                // 用户关闭了邮件通知
                sentNotificationIds.addAll(notifications.stream().map(Notification::getId).toList());
                emailsSkipped += notifications.size();
                continue;
            }

            // 静音时段检查（复用 NotificationService 的逻辑）
            if (pref != null && isInQuietHours(pref)) {
                // 在静音时段内不发送——下一轮再检查
                continue;
            }

            try {
                // 取最新的一条作为邮件主内容（聚合后的通知标题/内容已是最新）
                Notification latest = notifications.stream()
                        .max(Comparator.comparing(n -> n.getUpdatedAt() != null ? n.getUpdatedAt() : n.getCreatedAt()))
                        .orElse(notifications.get(0));

                String subject = "[TrackFlow] " + latest.getTitle();
                String htmlContent;

                if (notifications.size() > 1 || (latest.getAggregationCount() != null && latest.getAggregationCount() > 1)) {
                    // 多条通知或聚合通知 → 发送汇总邮件
                    int totalChanges = notifications.stream()
                            .mapToInt(n -> n.getAggregationCount() != null ? n.getAggregationCount() : 1)
                            .sum();
                    htmlContent = buildAggregatedEmailContent(latest.getTitle(), latest.getContent(), totalChanges);
                } else {
                    // 单条通知 → 普通邮件
                    htmlContent = buildNotificationEmailContent(latest.getTitle(), latest.getContent());
                }

                emailSendService.sendNotificationEmail(user.getEmail(), subject, htmlContent);
                sentNotificationIds.addAll(notifications.stream().map(Notification::getId).toList());
                emailsSent++;
            } catch (Exception e) {
                log.warn("[NotificationMail] 邮件发送失败: userId={}, resourceType={}, resourceId={}, error={}",
                        key.userId(), key.resourceType(), key.resourceId(), e.getMessage());
                // 发送失败的不标记，下一轮重试
            }
        }

        // 批量标记已发送
        if (!sentNotificationIds.isEmpty()) {
            notificationService.markMailSentByIds(sentNotificationIds);
        }

        log.info("[NotificationMail] 处理完成: sent={}, skipped={}, pending_retry={}",
                emailsSent, emailsSkipped, pending.size() - sentNotificationIds.size());
    }

    /**
     * 构建普通通知邮件 HTML 内容
     */
    private String buildNotificationEmailContent(String title, String content) {
        return String.format("""
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h3 style="color: #1f2328; margin: 0 0 12px 0;">%s</h3>
                  <p style="color: #57606a; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0;">%s</p>
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 项目管理系统自动发送。您可以在个人通知偏好中关闭邮件通知。
                  </p>
                </div>
                """, escapeHtml(title), escapeHtml(content));
    }

    /**
     * 构建聚合/汇总通知邮件 HTML 内容
     */
    private String buildAggregatedEmailContent(String title, String content, int totalChanges) {
        return String.format("""
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h3 style="color: #1f2328; margin: 0 0 8px 0;">%s</h3>
                  <p style="color: #0969da; font-size: 12px; margin: 0 0 12px 0;">包含 %d 次变更的汇总通知</p>
                  <p style="color: #57606a; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0;">%s</p>
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 项目管理系统自动发送。聚合窗口内的多次变更已合并为此封邮件。
                    您可以在个人通知偏好中关闭邮件通知。
                  </p>
                </div>
                """, escapeHtml(title), totalChanges, escapeHtml(content));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 检查用户当前是否处于静音时段内（复制自 NotificationService 的逻辑）。
     */
    private boolean isInQuietHours(NotificationPreference pref) {
        if (pref == null) return false;
        String startStr = pref.getQuietHoursStart();
        String endStr = pref.getQuietHoursEnd();
        if (startStr == null || startStr.isBlank() || endStr == null || endStr.isBlank()) {
            return false;
        }
        try {
            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.LocalTime start = java.time.LocalTime.parse(startStr);
            java.time.LocalTime end = java.time.LocalTime.parse(endStr);
            if (start.equals(end)) return false;
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 邮件分组 key：同一用户 + 同一资源类型 + 同一资源 ID 的通知合并为一封邮件。
     */
    private record MailGroupKey(Long userId, String resourceType, Long resourceId) {}
}

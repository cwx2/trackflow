package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.entity.NotificationCategory;
import com.trackflow.integration.entity.NotificationPreference;
import com.trackflow.integration.entity.NotificationReason;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.vo.NotificationVO;
import com.trackflow.integration.converter.NotificationConverter;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationConverter notificationConverter;
    private final MutedThreadService mutedThreadService;
    private final EmailSendService emailSendService;
    private final NotificationPreferenceService preferenceService;
    private final SystemSettingService systemSettingService;
    private final NotificationUrlBuilder urlBuilder;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 通知聚合时间窗口（分钟）。同一用户+同一类型+同一资源在此窗口内的多次通知将被合并。
     */
    @Value("${trackflow.notification.aggregation-minutes:5}")
    private int aggregationMinutes;

    /**
     * 创建通知（含聚合去重逻辑 + 延迟邮件投递）。
     * <p>
     * 聚合规则：在 aggregation-minutes 时间窗口内，若存在同一 userId + type + resourceType + resourceId
     * 的未读通知，则更新该条通知的 title/content/actorId/updatedAt，并递增 aggregationCount，
     * 重置 isRead=false 和 mailSent=false。否则新建一条通知。
     * <p>
     * 邮件发送规则（两阶段模型，参考 OpenProject WorkflowJob）：
     * - mention 类型：立即发送邮件（紧急，不等待聚合窗口）
     * - 其他类型：延迟发送，由 {@link NotificationMailScheduler} 在聚合窗口结束后处理
     *   如果用户在等待期间已读 IAN（站内通知），则跳过邮件
     *
     * @param userId       接收者用户ID
     * @param actorId      触发者用户ID（系统自动通知时为 null）
     * @param title        通知标题
     * @param content      通知内容
     * @param type         通知类型（枚举约束，确保前后端同步）
     * @param reason       通知原因（为什么通知此用户，可为 null 向后兼容）
     * @param resourceType 关联资源类型
     * @param resourceId   关联资源ID
     * @param projectId    关联项目ID（可为 null，如全局/系统通知）
     */
    @Transactional(rollbackFor = Exception.class)
        public void notify(Long userId, Long actorId, String title, String content, NotificationType type,
                           NotificationReason reason, String resourceType, Long resourceId, Long projectId) {
        // 防御性校验：actor_id 不应为 null（除系统自动通知外）
        if (actorId == null && type != NotificationType.issue_auto_assigned
                && type != NotificationType.due_date_alert && type != NotificationType.overdue_alert) {
            log.warn("[Notification] actor_id 为 null: userId={}, type={}, resourceType={}, resourceId={}",
                    userId, type, resourceType, resourceId);
        }

        // 全局站内通知开关检查（管理员可通过 NotificationAdmin 设置关闭）
        if (!isInAppEnabled()) {
            log.debug("[Notification] 全局站内通知已关闭，跳过: userId={}, type={}", userId, type);
            return;
        }

        String typeValue = type.name();

        // 静音检查：@提及类型永远不被静音
        if (type != NotificationType.mention && resourceId != null) {
            if (mutedThreadService.isMuted(userId, resourceType, resourceId)) {
                log.debug("[Notification] 跳过已静音线程: userId={}, resourceType={}, resourceId={}",
                        userId, resourceType, resourceId);
                return;
            }
        }

        // 查找聚合窗口内的同类未读通知
        Notification existing = findRecentUnread(userId, typeValue, resourceType, resourceId);

        boolean isNew = false;
        if (existing != null) {
            // 聚合：更新已有通知
            existing.setTitle(title);
            existing.setContent(content);
            existing.setActorId(actorId);
            existing.setIsRead(false);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setAggregationCount(
                    (existing.getAggregationCount() != null ? existing.getAggregationCount() : 1) + 1);
            // 聚合时重置 mailSent=false，让定时任务在聚合窗口结束后重新评估是否需要发邮件
            existing.setMailSent(false);
            existing.setMailSentAt(null);
            // 聚合时不覆盖 reason（保留第一次的 reason）
            notificationMapper.updateById(existing);
            log.debug("[Notification] 聚合通知: id={}, userId={}, type={}, resourceId={}, count={}",
                    existing.getId(), userId, typeValue, resourceId, existing.getAggregationCount());
        } else {
            // 新建通知
            Notification n = new Notification();
            n.setUserId(userId);
            n.setActorId(actorId);
            n.setProjectId(projectId);
            n.setTitle(title);
            n.setContent(content);
            n.setType(typeValue);
            n.setReason(reason != null ? reason.name() : null);
            n.setResourceType(resourceType);
            n.setResourceId(resourceId);
            n.setResourceUrl(urlBuilder.buildPath(resourceType, resourceId, projectId));
            n.setIsRead(false);
            n.setMailSent(false);
            n.setCreatedAt(LocalDateTime.now());
            n.setAggregationCount(1);
            notificationMapper.insert(n);
            isNew = true;
        }

        // 邮件发送策略（参考 OpenProject WorkflowJob 两阶段模型）：
        // - mention 类型：立即发送邮件（用户可能不在线但需紧急关注）
        // - 其他类型：延迟发送，由 NotificationMailScheduler 定时任务在聚合窗口结束后处理
        //   如果用户在等待期间已读 IAN，则跳过邮件（避免"已知道了还收到邮件"的冗余打扰）
        if (isNew && type == NotificationType.mention) {
            String fullUrl = urlBuilder.buildFullUrl(resourceType, resourceId, projectId);
            boolean sent = dispatchEmail(userId, title, content, fullUrl);
            if (sent) {
                // 仅在邮件实际发送成功后才标记——失败的由 NotificationMailScheduler 60秒后重试
                markMailSent(userId, typeValue, resourceType, resourceId);
            }
        }

        // WebSocket 实时推送：通知用户有新通知（轻量事件，仅含必要信息）
        pushNotificationToUser(userId, title, typeValue, resourceType, resourceId);
    }

    /**
     * 创建通知（无 reason 的兼容重载，向后兼容旧调用方）。
     */
    @Transactional(rollbackFor = Exception.class)
        public void notify(Long userId, Long actorId, String title, String content, NotificationType type,
                           String resourceType, Long resourceId, Long projectId) {
        notify(userId, actorId, title, content, type, null, resourceType, resourceId, projectId);
    }

    /**
     * 创建通知（无 projectId 的兼容重载，用于不关联项目的系统通知）。
     */
    @Transactional(rollbackFor = Exception.class)
        public void notify(Long userId, Long actorId, String title, String content, NotificationType type,
                           String resourceType, Long resourceId) {
        notify(userId, actorId, title, content, type, null, resourceType, resourceId, null);
    }

    /**
     * 批量创建通知（含聚合去重逻辑），将 N 次单独 DB 操作优化为 2-3 次批量操作。
     * <p>
     * 适用场景：Sprint 启动/完成通知全部成员、项目归档/删除通知全部成员等。
     * <p>
     * 处理流程：
     * 1. 全局开关检查
     * 2. 批量静音过滤（一次 SELECT）
     * 3. 批量聚合窗口查询（一次 SELECT）
     * 4. 分组为 toInsert / toUpdate
     * 5. 批量 INSERT + 批量 UPDATE（各一次 DB 操作）
     * 6. 批量邮件分发
     *
     * @param userIds      接收者用户 ID 集合（已排除操作者、已过滤偏好的最终列表）
     * @param actorId      触发者用户 ID
     * @param title        通知标题
     * @param content      通知内容
     * @param type         通知类型
     * @param resourceType 关联资源类型
     * @param resourceId   关联资源 ID
     * @param projectId    关联项目 ID（可为 null）
     */
    @Transactional(rollbackFor = Exception.class)
        public void notifyBatch(Collection<Long> userIds, Long actorId, String title, String content,
                                NotificationType type, String resourceType, Long resourceId, Long projectId) {
        notifyBatch(userIds, actorId, title, content, type, null, resourceType, resourceId, projectId);
    }

    /**
     * 批量创建通知（含 reason），含聚合去重逻辑。
     */
    @Transactional(rollbackFor = Exception.class)
        public void notifyBatch(Collection<Long> userIds, Long actorId, String title, String content,
                                NotificationType type, NotificationReason reason,
                                String resourceType, Long resourceId, Long projectId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        // 全局站内通知开关检查
        if (!isInAppEnabled()) {
            log.debug("[Notification] 全局站内通知已关闭，跳过批量通知: type={}, recipients={}", type, userIds.size());
            return;
        }

        String typeValue = type.name();

        // 批量静音过滤（@提及类型永远不被静音）
        Set<Long> filteredUserIds;
        if (type != NotificationType.mention && resourceId != null) {
            Set<Long> mutedUserIds = mutedThreadService.getMutedUserIds(resourceType, resourceId, userIds);
            filteredUserIds = userIds.stream()
                    .filter(id -> !mutedUserIds.contains(id))
                    .collect(Collectors.toSet());
            if (filteredUserIds.isEmpty()) {
                log.debug("[Notification] 所有接收者已静音，跳过: type={}, resource={}:{}", type, resourceType, resourceId);
                return;
            }
        } else {
            filteredUserIds = new HashSet<>(userIds);
        }

        // 批量查找聚合窗口内的同类未读通知
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(aggregationMinutes);
        Map<Long, Notification> existingMap = findRecentUnreadBatch(filteredUserIds, typeValue, resourceType, resourceId, cutoff);

        List<Notification> toInsert = new ArrayList<>();
        List<Notification> toUpdate = new ArrayList<>();
        Set<Long> newNotificationUserIds = new HashSet<>();

        for (Long userId : filteredUserIds) {
            Notification existing = existingMap.get(userId);
            if (existing != null) {
                // 聚合：更新已有通知
                existing.setTitle(title);
                existing.setContent(content);
                existing.setActorId(actorId);
                existing.setIsRead(false);
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setAggregationCount(
                        (existing.getAggregationCount() != null ? existing.getAggregationCount() : 1) + 1);
                // 聚合时重置 mailSent=false，让定时任务在聚合窗口结束后重新评估
                existing.setMailSent(false);
                existing.setMailSentAt(null);
                toUpdate.add(existing);
            } else {
                // 新建通知
                Notification n = new Notification();
                n.setUserId(userId);
                n.setActorId(actorId);
                n.setProjectId(projectId);
                n.setTitle(title);
                n.setContent(content);
                n.setType(typeValue);
                n.setReason(reason != null ? reason.name() : null);
                n.setResourceType(resourceType);
                n.setResourceId(resourceId);
                n.setResourceUrl(urlBuilder.buildPath(resourceType, resourceId, projectId));
                n.setIsRead(false);
                n.setMailSent(false);
                n.setCreatedAt(LocalDateTime.now());
                n.setAggregationCount(1);
                toInsert.add(n);
                newNotificationUserIds.add(userId);
            }
        }

        // 批量 INSERT
        if (!toInsert.isEmpty()) {
            Db.saveBatch(toInsert);
            log.debug("[Notification] 批量插入通知: count={}, type={}", toInsert.size(), typeValue);
        }

        // 批量 UPDATE（使用 MyBatis-Plus 批量操作，避免 N 次独立 SQL）
        if (!toUpdate.isEmpty()) {
            Db.updateBatchById(toUpdate);
            log.debug("[Notification] 批量聚合更新通知: count={}, type={}", toUpdate.size(), typeValue);
        }

        // 邮件发送策略（与 notify() 一致）：
        // - mention 类型：立即批量发送邮件
        // - 其他类型：延迟发送，由 NotificationMailScheduler 处理
        if (!newNotificationUserIds.isEmpty() && type == NotificationType.mention) {
            String fullUrl = urlBuilder.buildFullUrl(resourceType, resourceId, projectId);
            Set<Long> sentIds = dispatchEmailBatch(newNotificationUserIds, title, content, fullUrl);
            if (!sentIds.isEmpty()) {
                // 仅标记实际发送成功的用户——失败的由 NotificationMailScheduler 60秒后重试
                markMailSentBatch(sentIds, typeValue, resourceType, resourceId);
            }
        }

        // WebSocket 实时推送：通知所有接收者有新通知
        for (Long userId : filteredUserIds) {
            pushNotificationToUser(userId, title, typeValue, resourceType, resourceId);
        }
    }

    /**
     * 批量查找聚合窗口内同类型同资源的未读通知（按 userId 分组）。
     *
     * @return Map: userId → 匹配的最近一条未读通知
     */
    private Map<Long, Notification> findRecentUnreadBatch(Collection<Long> userIds, String type,
                                                          String resourceType, Long resourceId,
                                                          LocalDateTime cutoff) {
        if (resourceId == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Notification> existing = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .in(Notification::getUserId, userIds)
                        .eq(Notification::getType, type)
                        .eq(Notification::getResourceType, resourceType)
                        .eq(Notification::getResourceId, resourceId)
                        .eq(Notification::getIsRead, false)
                        .ge(Notification::getCreatedAt, cutoff)
                        .orderByDesc(Notification::getCreatedAt)
        );
        // 每个 userId 只取最近一条
        Map<Long, Notification> result = new HashMap<>();
        for (Notification n : existing) {
            result.putIfAbsent(n.getUserId(), n);
        }
        return result;
    }

    /**
     * 标记单个用户的通知邮件已发送（用于 mention 立即发送后标记）。
     */
    private void markMailSent(Long userId, String type, String resourceType, Long resourceId) {
        Notification update = new Notification();
        update.setMailSent(true);
        update.setMailSentAt(LocalDateTime.now());
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getType, type)
                        .eq(Notification::getResourceType, resourceType)
                        .eq(Notification::getResourceId, resourceId)
                        .eq(Notification::getMailSent, false)
        );
    }

    /**
     * 批量标记通知邮件已发送（用于 mention 批量立即发送后标记）。
     */
    private void markMailSentBatch(Collection<Long> userIds, String type, String resourceType, Long resourceId) {
        if (userIds.isEmpty()) return;
        Notification update = new Notification();
        update.setMailSent(true);
        update.setMailSentAt(LocalDateTime.now());
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .in(Notification::getUserId, userIds)
                        .eq(Notification::getType, type)
                        .eq(Notification::getResourceType, resourceType)
                        .eq(Notification::getResourceId, resourceId)
                        .eq(Notification::getMailSent, false)
        );
    }

    /**
     * 查询聚合窗口已结束且邮件未发送的未读通知（供 NotificationMailScheduler 使用）。
     * <p>
     * 条件：mail_sent=false AND is_read=false AND created_at < NOW() - aggregation_minutes
     * 聚合通知看 updated_at（聚合窗口最后更新时间），新建通知看 created_at。
     *
     * @param limit 一次最多处理条数
     * @return 待发邮件的通知列表
     */
    public List<Notification> findPendingMailNotifications(int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(aggregationMinutes);
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getMailSent, false)
                        .eq(Notification::getIsRead, false)
                        // 聚合窗口已结束：COALESCE(updated_at, created_at) < cutoff
                        .apply("COALESCE(updated_at, created_at) < {0}", cutoff)
                        .orderByAsc(Notification::getCreatedAt)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * 查询邮件未发送但用户已读的通知（IAN 已读抑制邮件）。
     * 这些通知用户已在站内看过，不需要再发邮件——直接标记 mail_sent=true。
     *
     * @return 被抑制的通知数量
     */
    @Transactional(rollbackFor = Exception.class)
        public int suppressReadNotificationMails() {
        Notification update = new Notification();
        update.setMailSent(true);
        update.setMailSentAt(LocalDateTime.now());
        return Math.toIntExact(notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getMailSent, false)
                        .eq(Notification::getIsRead, true)
        ));
    }

    /**
     * 批量标记通知邮件已发送（按 ID 列表）。
     */
    @Transactional(rollbackFor = Exception.class)
        public void markMailSentByIds(Collection<Long> notificationIds) {
        if (notificationIds.isEmpty()) return;
        Notification update = new Notification();
        update.setMailSent(true);
        update.setMailSentAt(LocalDateTime.now());
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .in(Notification::getId, notificationIds)
        );
    }

    /**
     * 批量分发邮件通知（仅对新建通知的用户）。
     * 批量查询用户信息和偏好，减少 DB 操作。
     * 跳过处于静音时段内的用户。
     *
     * @return 成功发送邮件的用户 ID 集合（不含被跳过或发送失败的用户）
     */
    private Set<Long> dispatchEmailBatch(Set<Long> userIds, String title, String content, String resourceUrl) {
        Set<Long> successfulIds = new HashSet<>();
        try {
            if (!emailSendService.isEmailAvailable()) {
                return successfulIds;
            }
            // 批量查询用户
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            if (users.isEmpty()) {
                return successfulIds;
            }
            // 批量查询全局偏好
            List<NotificationPreference> prefs = preferenceService.listGlobalByUserIds(userIds);
            Set<Long> emailEnabledUserIds = prefs.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getEmailEnabled()))
                    .filter(p -> !isInQuietHours(p))
                    .map(NotificationPreference::getUserId)
                    .collect(Collectors.toSet());

            String subject = "[TrackFlow] " + title;
            String htmlContent = buildNotificationEmailContent(title, content, resourceUrl);

            for (SysUser user : users) {
                if (user.getEmail() == null || user.getEmail().isBlank()) {
                    continue;
                }
                if (!emailEnabledUserIds.contains(user.getId())) {
                    continue;
                }
                try {
                    emailSendService.sendNotificationEmail(user.getEmail(), subject, htmlContent);
                    successfulIds.add(user.getId());
                } catch (Exception e) {
                    log.warn("[Notification] 批量邮件发送异常: userId={}, error={}", user.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[Notification] 批量邮件分发异常（不影响站内通知）: error={}", e.getMessage());
        }
        return successfulIds;
    }

    /**
     * 如果全局邮件通知已启用，且用户偏好中 emailEnabled=true，且用户有邮箱地址，
     * 且当前不在用户的静音时段内，则异步发送通知邮件。发送失败不影响站内通知。
     *
     * @return true 表示邮件成功发送，false 表示发送失败或被跳过（偏好关闭/无邮箱/静音时段等）
     */
    private boolean dispatchEmail(Long userId, String title, String content, String resourceUrl) {
        try {
            if (!emailSendService.isEmailAvailable()) {
                return false;
            }
            // 检查用户偏好是否开启了邮件
            var pref = preferenceService.getByUserId(userId);
            if (!Boolean.TRUE.equals(pref.getEmailEnabled())) {
                return false;
            }
            // 静音时段检查：在 quiet hours 内跳过邮件发送
            if (isInQuietHours(pref)) {
                log.debug("[Notification] 用户处于静音时段，跳过邮件: userId={}, quietHours={}-{}",
                        userId, pref.getQuietHoursStart(), pref.getQuietHoursEnd());
                return false;
            }
            // 获取用户邮箱
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return false;
            }
            // 发送邮件
            String subject = "[TrackFlow] " + title;
            String htmlContent = buildNotificationEmailContent(title, content, resourceUrl);
            emailSendService.sendNotificationEmail(user.getEmail(), subject, htmlContent);
            return true;
        } catch (Exception e) {
            log.warn("[Notification] 邮件分发异常（不影响站内通知）: userId={}, error={}",
                    userId, e.getMessage());
            return false;
        }
    }

    /**
     * 构建通知邮件 HTML 内容（含资源直链按钮）
     */
    private String buildNotificationEmailContent(String title, String content, String resourceUrl) {
        String actionButton = "";
        if (resourceUrl != null && !resourceUrl.isBlank()) {
            actionButton = String.format("""
                      <p style="margin: 0 0 24px 0;">
                        <a href="%s" style="display: inline-block; padding: 10px 20px; background-color: #0969da; color: #ffffff; text-decoration: none; border-radius: 6px; font-size: 14px; font-weight: 500;">在 TrackFlow 中查看</a>
                      </p>
                    """, resourceUrl);
        }
        return String.format("""
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px;">
                  <h3 style="color: #1f2328; margin: 0 0 12px 0;">%s</h3>
                  <p style="color: #57606a; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0;">%s</p>
                  %s\
                  <hr style="border: none; border-top: 1px solid #d1d9e0; margin: 24px 0;" />
                  <p style="color: #8b949e; font-size: 12px;">
                    此邮件由 TrackFlow 项目管理系统自动发送。您可以在个人通知偏好中关闭邮件通知。
                  </p>
                </div>
                """, escapeHtml(title), escapeHtml(content), actionButton);
    }

    /**
     * 简单 HTML 转义，防止通知内容中的特殊字符破坏邮件结构
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 检查用户当前是否处于静音时段内。
     * <p>
     * 静音时段影响邮件发送——在 quiet hours 内跳过邮件，站内通知不受影响。
     * 支持跨午夜场景（如 22:00 - 08:00）。
     *
     * @param pref 用户通知偏好（包含 quietHoursStart / quietHoursEnd）
     * @return true 表示当前处于静音时段
     */
    private boolean isInQuietHours(NotificationPreference pref) {
        if (pref == null) {
            return false;
        }
        String startStr = pref.getQuietHoursStart();
        String endStr = pref.getQuietHoursEnd();
        if (startStr == null || startStr.isBlank() || endStr == null || endStr.isBlank()) {
            return false;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);

            if (start.equals(end)) {
                // 起止相同视为未设置
                return false;
            }
            if (start.isBefore(end)) {
                // 非跨午夜：如 09:00 - 18:00
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                // 跨午夜：如 22:00 - 08:00（不在 08:00~22:00 之间即为静音时段）
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            log.warn("[Notification] 解析静音时段失败: start={}, end={}, error={}",
                    startStr, endStr, e.getMessage());
            return false;
        }
    }

    /**
     * 查找聚合时间窗口内同类型同资源的未读通知。
     *
     * @return 匹配的最近一条未读通知，不存在返回 null
     */
    private Notification findRecentUnread(Long userId, String type, String resourceType, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(aggregationMinutes);
        return notificationMapper.selectOne(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getType, type)
                        .eq(Notification::getResourceType, resourceType)
                        .eq(Notification::getResourceId, resourceId)
                        .eq(Notification::getIsRead, false)
                        .ge(Notification::getCreatedAt, cutoff)
                        .orderByDesc(Notification::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    /**
     * 获取用户通知列表（支持分类过滤、项目过滤和原因过滤）。
     * 排序按 COALESCE(updated_at, created_at) DESC，聚合更新的通知置顶。
     */
    public Page<Notification> list(Long userId, Boolean unreadOnly, NotificationCategory category, Long projectId, String reason, Page<Notification> page) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Notification::getIsRead, false);
        }
        // 项目过滤
        if (projectId != null) {
            wrapper.eq(Notification::getProjectId, projectId);
        }
        // 原因过滤
        if (reason != null && !reason.isBlank()) {
            wrapper.eq(Notification::getReason, reason);
        }
        // 分类过滤
        applyCategory(wrapper, category);
        // 使用 apply 自定义排序：聚合更新的通知优先
        wrapper.last("ORDER BY COALESCE(updated_at, created_at) DESC");
        return notificationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取通知列表并填充 actor 信息（批量查询用户，避免 N+1）
     */
    public PageResult<NotificationVO> listWithActor(Long userId, Boolean unreadOnly, NotificationCategory category, Long projectId, String reason, Page<Notification> page) {
        Page<Notification> result = list(userId, unreadOnly, category, projectId, reason, page);
        List<Notification> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L,
                    (int) result.getCurrent(), (int) result.getSize());
        }

        // 收集所有 actorId（去空去重）
        Set<Long> actorIds = records.stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 批量查询用户信息
        Map<Long, SysUser> userMap = Collections.emptyMap();
        if (!actorIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(actorIds);
            userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        }

        // 批量查询静音状态
        Set<Long> resourceIds = records.stream()
                .map(Notification::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> mutedResourceIds = mutedThreadService.getMutedResourceIds(userId, "issue", resourceIds);

        // 转换为 VO 并填充 actor 信息和静音状态
        List<NotificationVO> voList = notificationConverter.toVOList(records);
        for (int i = 0; i < voList.size(); i++) {
            NotificationVO vo = voList.get(i);
            Notification record = records.get(i);
            Long actorId = record.getActorId();
            if (actorId != null) {
                SysUser actor = userMap.get(actorId);
                if (actor != null) {
                    vo.setActorName(actor.getDisplayName());
                    vo.setActorAvatar(actor.getAvatarUrl());
                }
            }
            // 设置静音状态
            vo.setResourceMuted(record.getResourceId() != null && mutedResourceIds.contains(record.getResourceId()));
            // 设置 reason 中文标签
            if (record.getReason() != null && !record.getReason().isBlank()) {
                try {
                    NotificationReason reasonEnum = NotificationReason.valueOf(record.getReason());
                    vo.setReasonLabel(reasonEnum.getDisplayLabel());
                } catch (IllegalArgumentException e) {
                    // 未知 reason 值，保留 null
                }
            }
        }
        return new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 获取各分类的未读计数。
     * 返回 Map: { "all": N, "mention": N, "subscription": N, "system": N }
     */
    public Map<String, Long> unreadCountByCategory(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (NotificationCategory cat : NotificationCategory.values()) {
            LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Notification::getUserId, userId)
                    .eq(Notification::getIsRead, false);
            applyCategory(wrapper, cat);
            counts.put(cat.name(), notificationMapper.selectCount(wrapper));
        }
        return counts;
    }

    /**
     * 标记已读（带所有权校验）
     */
    @Transactional(rollbackFor = Exception.class)
        public void markRead(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        n.setIsRead(true);
        notificationMapper.updateById(n);
    }

    /**
     * 标记未读（恢复未读状态，带所有权校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markUnread(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        n.setIsRead(false);
        notificationMapper.updateById(n);
    }

    /**
     * 全部标记已读
     */
    @Transactional(rollbackFor = Exception.class)
        public void markAllRead(Long userId) {
        Notification update = new Notification();
        update.setIsRead(true);
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
        );
    }

    /**
     * 未读数量
     */
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
        );
    }

    /**
     * 删除单条通知（带所有权校验）
     */
    @Transactional(rollbackFor = Exception.class)
        public void delete(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        notificationMapper.deleteById(id);
    }

    /**
     * 删除当前用户所有已读通知
     */
    @Transactional(rollbackFor = Exception.class)
        public int deleteAllRead(Long userId) {
        return Math.toIntExact(notificationMapper.delete(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, true)
        ));
    }

    /**
     * 清理指定天数前的已读通知（定时任务调用）
     *
     * @param retentionDays 保留天数
     * @return 清理数量
     */
    @Transactional(rollbackFor = Exception.class)
        public int cleanupExpiredNotifications(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deleted = notificationMapper.delete(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getIsRead, true)
                        .lt(Notification::getCreatedAt, cutoff)
        );
        return Math.toIntExact(deleted);
    }

    /**
     * 将分类条件应用到查询 wrapper 上。
     * all 分类不加任何类型过滤条件。
     */
    private void applyCategory(LambdaQueryWrapper<Notification> wrapper, NotificationCategory category) {
        if (category == null || category == NotificationCategory.all) {
            return;
        }
        Set<String> typeNames = category.getTypeNames();
        if (!typeNames.isEmpty()) {
            wrapper.in(Notification::getType, typeNames);
        }
    }

    /**
     * 检查全局站内通知是否启用。
     * 读取 system_setting 表中 key="notification.in_app_enabled" 的值，默认为 true。
     */
    private boolean isInAppEnabled() {
        String value = systemSettingService.getSettingValue("notification.in_app_enabled", "true");
        return Boolean.parseBoolean(value);
    }

    /**
     * 通过 WebSocket 推送轻量通知事件到指定用户。
     * <p>
     * 推送目的地：/user/{userId}/queue/notifications
     * 推送内容：仅含未读数增量提示和最新通知摘要，前端收到后刷新 badge 和列表。
     * <p>
     * 推送失败不影响业务（用户可能不在线或 WebSocket 未连接），仅记录 debug 日志。
     */
    private void pushNotificationToUser(Long userId, String title, String type, String resourceType, Long resourceId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "NEW_NOTIFICATION");
            payload.put("title", title);
            payload.put("type", type);
            payload.put("resourceType", resourceType);
            payload.put("resourceId", resourceId != null ? resourceId.toString() : null);
            payload.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    payload
            );
            log.debug("[Notification] WebSocket 推送: userId={}, type={}", userId, type);
        } catch (Exception e) {
            // WebSocket 推送失败不影响业务——用户可能不在线，轮询仍可兜底
            log.debug("[Notification] WebSocket 推送失败（用户可能离线）: userId={}, error={}", userId, e.getMessage());
        }
    }
}

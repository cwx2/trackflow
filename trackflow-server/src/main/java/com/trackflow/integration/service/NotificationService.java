package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.entity.NotificationCategory;
import com.trackflow.integration.entity.NotificationType;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.vo.NotificationVO;
import com.trackflow.integration.converter.NotificationConverter;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * 通知聚合时间窗口（分钟）。同一用户+同一类型+同一资源在此窗口内的多次通知将被合并。
     */
    @Value("${trackflow.notification.aggregation-minutes:5}")
    private int aggregationMinutes;

    /**
     * 创建通知（含聚合去重逻辑）。
     * <p>
     * 聚合规则：在 aggregation-minutes 时间窗口内，若存在同一 userId + type + resourceType + resourceId
     * 的未读通知，则更新该条通知的 title/content/actorId/updatedAt，并递增 aggregationCount，
     * 重置 isRead=false。否则新建一条通知。
     * <p>
     * 邮件发送规则：站内通知创建/聚合后，若全局 emailEnabled 且用户偏好 emailEnabled，
     * 异步发送邮件通知（仅新建时发送，聚合更新不重复发邮件）。
     * <p>
     * 参考 OpenProject 的 update_or_create_notification 设计。
     *
     * @param userId       接收者用户ID
     * @param actorId      触发者用户ID（系统自动通知时为 null）
     * @param title        通知标题
     * @param content      通知内容
     * @param type         通知类型（枚举约束，确保前后端同步）
     * @param resourceType 关联资源类型
     * @param resourceId   关联资源ID
     */
    @Transactional
    public void notify(Long userId, Long actorId, String title, String content, NotificationType type,
                       String resourceType, Long resourceId) {
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
            notificationMapper.updateById(existing);
            log.debug("[Notification] 聚合通知: id={}, userId={}, type={}, resourceId={}, count={}",
                    existing.getId(), userId, typeValue, resourceId, existing.getAggregationCount());
        } else {
            // 新建通知
            Notification n = new Notification();
            n.setUserId(userId);
            n.setActorId(actorId);
            n.setTitle(title);
            n.setContent(content);
            n.setType(typeValue);
            n.setResourceType(resourceType);
            n.setResourceId(resourceId);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            n.setAggregationCount(1);
            notificationMapper.insert(n);
            isNew = true;
        }

        // 邮件发送（仅新建通知时触发，聚合更新不重复发送邮件）
        if (isNew) {
            dispatchEmail(userId, title, content);
        }
    }

    /**
     * 如果全局邮件通知已启用，且用户偏好中 emailEnabled=true，且用户有邮箱地址，
     * 则异步发送通知邮件。发送失败不影响站内通知。
     */
    private void dispatchEmail(Long userId, String title, String content) {
        try {
            if (!emailSendService.isEmailAvailable()) {
                return;
            }
            // 检查用户偏好是否开启了邮件
            var pref = preferenceService.getByUserId(userId);
            if (!Boolean.TRUE.equals(pref.getEmailEnabled())) {
                return;
            }
            // 获取用户邮箱
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            // 异步发送
            String subject = "[TrackFlow] " + title;
            String htmlContent = buildNotificationEmailContent(title, content);
            emailSendService.sendNotificationEmail(user.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            log.warn("[Notification] 邮件分发异常（不影响站内通知）: userId={}, error={}",
                    userId, e.getMessage());
        }
    }

    /**
     * 构建通知邮件 HTML 内容
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
     * 获取用户通知列表（支持分类过滤）。
     * 排序按 COALESCE(updated_at, created_at) DESC，聚合更新的通知置顶。
     */
    public Page<Notification> list(Long userId, Boolean unreadOnly, NotificationCategory category, Page<Notification> page) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Notification::getIsRead, false);
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
    public PageResult<NotificationVO> listWithActor(Long userId, Boolean unreadOnly, NotificationCategory category, Page<Notification> page) {
        Page<Notification> result = list(userId, unreadOnly, category, page);
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
    @Transactional
    public void markRead(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        n.setIsRead(true);
        notificationMapper.updateById(n);
    }

    /**
     * 全部标记已读
     */
    @Transactional
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
    @Transactional
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
    @Transactional
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
    @Transactional
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
}

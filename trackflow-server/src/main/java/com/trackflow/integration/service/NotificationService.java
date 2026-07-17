package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.mapper.NotificationMapper;
import com.trackflow.integration.vo.NotificationVO;
import com.trackflow.integration.converter.NotificationConverter;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationConverter notificationConverter;

    /**
     * 创建通知（含触发者信息）
     *
     * @param userId       接收者用户ID
     * @param actorId      触发者用户ID（系统自动通知时为 null）
     * @param title        通知标题
     * @param content      通知内容
     * @param type         通知类型
     * @param resourceType 关联资源类型
     * @param resourceId   关联资源ID
     */
    public void notify(Long userId, Long actorId, String title, String content, String type,
                       String resourceType, Long resourceId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setActorId(actorId);
        n.setTitle(title);
        n.setContent(content);
        n.setType(type);
        n.setResourceType(resourceType);
        n.setResourceId(resourceId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /**
     * 获取用户通知列表（含触发者信息）
     */
    public Page<Notification> list(Long userId, Boolean unreadOnly, Page<Notification> page) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Notification::getIsRead, false);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        return notificationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取通知列表并填充 actor 信息（批量查询用户，避免 N+1）
     */
    public PageResult<NotificationVO> listWithActor(Long userId, Boolean unreadOnly, Page<Notification> page) {
        Page<Notification> result = list(userId, unreadOnly, page);
        List<Notification> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0L,
                    (int) result.getCurrent(), (int) result.getSize());
        }

        // 收集所有 actorId（去空去重）
        Set<Long> actorIds = records.stream()
                .map(Notification::getActorId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 批量查询用户信息
        Map<Long, SysUser> userMap = Collections.emptyMap();
        if (!actorIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(actorIds);
            userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        }

        // 转换为 VO 并填充 actor 信息
        List<NotificationVO> voList = notificationConverter.toVOList(records);
        for (int i = 0; i < voList.size(); i++) {
            NotificationVO vo = voList.get(i);
            Long actorId = records.get(i).getActorId();
            if (actorId != null) {
                SysUser actor = userMap.get(actorId);
                if (actor != null) {
                    vo.setActorName(actor.getDisplayName());
                    vo.setActorAvatar(actor.getAvatarUrl());
                }
            }
        }
        return new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
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
}

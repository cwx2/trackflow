package com.trackflow.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.integration.entity.Notification;
import com.trackflow.integration.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 创建通知
     */
    public void notify(Long userId, String title, String content, String type,
                       String resourceType, Long resourceId) {
        Notification n = new Notification();
        n.setUserId(userId);
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
     * 获取用户通知列表
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
     * 标记已读
     */
    @Transactional
    public void markRead(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n != null) {
            n.setIsRead(true);
            notificationMapper.updateById(n);
        }
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
}

package com.trackflow.integration.service;

/**
 * 通知 WebSocket 推送事件。
 * <p>
 * 由 NotificationService 在通知写入 DB 后发布，
 * 由 NotificationWebSocketListener 在事务提交后通过 @TransactionalEventListener 处理，
 * 确保前端 fetchUnreadCount 时能查到已提交的通知数据。
 */
public record NotificationPushEvent(
        Long userId,
        String title,
        String type,
        String resourceType,
        Long resourceId
) {
}

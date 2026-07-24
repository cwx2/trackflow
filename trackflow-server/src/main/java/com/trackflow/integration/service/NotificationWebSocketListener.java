package com.trackflow.integration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知 WebSocket 推送监听器。
 * <p>
 * 监听 NotificationPushEvent，在事务提交后通过 WebSocket 推送轻量事件到指定用户。
 * 使用与 IssueRealtimeListener 相同的 @TransactionalEventListener(AFTER_COMMIT) + @Async 模式。
 * <p>
 * 推送 topic: /topic/users/{userId}/notifications
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationPush(NotificationPushEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "NEW_NOTIFICATION");
            payload.put("title", event.title());
            payload.put("type", event.type());
            payload.put("resourceType", event.resourceType());
            payload.put("resourceId", event.resourceId() != null ? event.resourceId().toString() : null);
            payload.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/users/" + event.userId() + "/notifications",
                    payload
            );
            log.debug("[Notification] WebSocket 推送: userId={}, type={}", event.userId(), event.type());
        } catch (Exception e) {
            log.debug("[Notification] WebSocket 推送失败（用户可能离线）: userId={}, error={}", event.userId(), e.getMessage());
        }
    }
}

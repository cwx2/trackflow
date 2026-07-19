package com.trackflow.integration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * 通知发件箱写入器：提供给 NotificationHelper 类在 catch 块中将失败事件写入 outbox 的便捷方法。
 * <p>
 * 使用方式：
 * <pre>
 * try {
 *     // 通知发送逻辑
 * } catch (Exception e) {
 *     log.error("...");
 *     outboxWriter.saveForRetry("notifyAssigned", e, Map.of(
 *         "method", "notify",
 *         "userId", assigneeId,
 *         "actorId", operatorId,
 *         ...
 *     ));
 * }
 * </pre>
 * <p>
 * 如果 outbox 写入本身也失败（极端情况：DB 完全不可用），仅 log.error 不抛出异常，
 * 避免在异常处理中引发新异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxWriter {

    private final NotificationOutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * 将失败的通知事件保存到发件箱以便后续重试。
     *
     * @param eventType 事件类型标识（方法名，如 "notifyAssigned"）
     * @param error     原始异常
     * @param params    通知参数 Map（将被序列化为 JSON payload）
     */
    public void saveForRetry(String eventType, Exception error, Map<String, Object> params) {
        try {
            String payload = objectMapper.writeValueAsString(params);
            String errorMessage = error != null ? error.getMessage() : "unknown error";
            outboxService.saveFailedEvent(eventType, payload, errorMessage);
        } catch (Exception e) {
            // 极端情况：连 outbox 都写不进去（DB 完全不可用）
            // 仅记录日志，不抛出异常——避免在异常处理中引发新异常
            log.error("[NotificationOutboxWriter] 无法写入发件箱（DB 可能不可用）: eventType={}, originalError={}, outboxError={}",
                    eventType, error != null ? error.getMessage() : "null", e.getMessage());
        }
    }

    /**
     * 构建通知参数 Map 的便捷方法（单用户通知）。
     */
    public Map<String, Object> buildNotifyParams(Long userId, Long actorId, String title, String content,
                                                  String type, String reason, String resourceType,
                                                  Long resourceId, Long projectId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("method", "notify");
        params.put("userId", userId);
        params.put("actorId", actorId);
        params.put("title", title);
        params.put("content", content);
        params.put("type", type);
        params.put("reason", reason);
        params.put("resourceType", resourceType);
        params.put("resourceId", resourceId);
        params.put("projectId", projectId);
        return params;
    }

    /**
     * 构建通知参数 Map 的便捷方法（批量通知）。
     */
    public Map<String, Object> buildBatchParams(Collection<Long> userIds, Long actorId, String title, String content,
                                                 String type, String reason, String resourceType,
                                                 Long resourceId, Long projectId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("method", "notifyBatch");
        params.put("userIds", new ArrayList<>(userIds));
        params.put("actorId", actorId);
        params.put("title", title);
        params.put("content", content);
        params.put("type", type);
        params.put("reason", reason);
        params.put("resourceType", resourceType);
        params.put("resourceId", resourceId);
        params.put("projectId", projectId);
        return params;
    }
}

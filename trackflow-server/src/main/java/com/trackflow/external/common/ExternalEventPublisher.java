package com.trackflow.external.common;

import com.trackflow.external.mapper.ExternalEventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部集成事件发布器。
 * <p>
 * 负责将出站事件分发给所有匹配的 {@link ExternalAdapter} 并记录日志。
 * <p>
 * 工作流程：
 * 1. external 模块的 EventListener 监听核心事件（如 IssueNotificationEvent.Created）
 * 2. 构造 ExternalEvent 对象
 * 3. 调用 publish() 发布到所有已注册的适配器
 * 4. 每次调用自动记录到 external_event_log 表
 * <p>
 * 设计要点：
 * - 核心模块不依赖此类（解耦）
 * - 适配器通过 Spring 自动注入发现（SPI 模式）
 * - 单个适配器失败不影响其他适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalEventPublisher {

    private final ExternalEventLogMapper eventLogMapper;
    private final List<ExternalAdapter> adapters;

    /**
     * 发布出站事件到所有匹配的适配器。
     * <p>
     * 每个适配器独立执行，单个失败不影响其他适配器。
     * 所有调用结果（成功/失败）都记录到 external_event_log。
     *
     * @param event 出站事件
     */
    public void publish(ExternalEvent event) {
        ExternalEventType eventType = ExternalEventType.fromCode(event.getEventType());
        if (eventType == null) {
            log.warn("[ExternalEventPublisher] 未知事件类型: {}", event.getEventType());
            return;
        }

        for (ExternalAdapter adapter : adapters) {
            if (!adapter.isEnabled()) {
                continue;
            }
            if (!adapter.getSupportedEvents().contains(eventType)) {
                continue;
            }

            ExternalEventLog logEntry = createLogEntry(event, adapter.getAdapterType());

            try {
                logEntry.setStatus("processing");
                eventLogMapper.updateById(logEntry);

                adapter.handle(event);

                logEntry.setStatus("success");
                logEntry.setUpdatedAt(LocalDateTime.now());
                eventLogMapper.updateById(logEntry);

                log.debug("[ExternalEventPublisher] 事件已成功分发: adapter={}, event={}",
                        adapter.getAdapterType(), event.getEventType());

            } catch (Exception e) {
                log.error("[ExternalEventPublisher] 适配器执行失败: adapter={}, event={}, error={}",
                        adapter.getAdapterType(), event.getEventType(), e.getMessage(), e);

                logEntry.setStatus("failed");
                logEntry.setErrorMessage(truncateMessage(e.getMessage(), 2000));
                logEntry.setRetryCount(logEntry.getRetryCount() + 1);

                // 计算下次重试时间（指数退避）
                if (logEntry.getRetryCount() < adapter.getMaxRetries()) {
                    int delaySeconds = adapter.getRetryIntervalSeconds() * (int) Math.pow(2, logEntry.getRetryCount() - 1);
                    logEntry.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
                } else {
                    logEntry.setNextRetryAt(null); // 超过最大重试次数
                }

                logEntry.setUpdatedAt(LocalDateTime.now());
                eventLogMapper.updateById(logEntry);
            }
        }
    }

    /**
     * 创建并持久化初始日志条目
     */
    private ExternalEventLog createLogEntry(ExternalEvent event, String adapterType) {
        ExternalEventLog logEntry = new ExternalEventLog();
        logEntry.setAdapterType(adapterType);
        logEntry.setEventType(event.getEventType());
        logEntry.setDirection(event.getDirection().name().toLowerCase());
        logEntry.setReferenceId(event.getReferenceId());
        logEntry.setPayload(event.getPayload());
        logEntry.setStatus("pending");
        logEntry.setRetryCount(0);
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        eventLogMapper.insert(logEntry);
        return logEntry;
    }

    /**
     * 截断错误消息，避免超出数据库字段长度
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return null;
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}

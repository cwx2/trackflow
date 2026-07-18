package com.trackflow.external.common;

import java.time.LocalDateTime;

/**
 * 出站事件基类。
 * <p>
 * 所有对外集成事件（Email 通知、SUG 同步、Webhook 推送等）都继承此基类。
 * 由 {@link ExternalEventPublisher} 统一发布，自动记录到 external_event_log 表。
 * <p>
 * 设计要点：
 * - 核心模块（issue/project/sprint）不依赖本类
 * - 外部集成模块通过监听核心的 Spring Event 来构造 ExternalEvent 并发布
 * - 每个 ExternalEvent 对应一条 external_event_log 记录
 */
public abstract class ExternalEvent {

    /** 适配器类型（如 "email", "sug", "webhook"） */
    private final String adapterType;

    /** 事件类型（如 "issue.created", "issue.status_changed"） */
    private final String eventType;

    /** 方向：OUTBOUND = 向外推送, INBOUND = 外部接收 */
    private final Direction direction;

    /** 关联的业务实体 ID（如 issueId、projectId 等） */
    private final String referenceId;

    /** 事件载荷（JSON 序列化后的业务数据） */
    private final String payload;

    /** 事件创建时间 */
    private final LocalDateTime createdAt;

    protected ExternalEvent(String adapterType, String eventType, Direction direction,
                            String referenceId, String payload) {
        this.adapterType = adapterType;
        this.eventType = eventType;
        this.direction = direction;
        this.referenceId = referenceId;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public String getAdapterType() {
        return adapterType;
    }

    public String getEventType() {
        return eventType;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 事件方向
     */
    public enum Direction {
        /** 向外推送（TrackFlow → 外部系统） */
        OUTBOUND,
        /** 外部接收（外部系统 → TrackFlow） */
        INBOUND
    }
}

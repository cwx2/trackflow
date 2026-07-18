package com.trackflow.external.common;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外部集成事件日志 Entity（对应 external_event_log 表）。
 * <p>
 * 记录每次出站/入站集成事件的详细信息，支持：
 * - 事件审计（谁在什么时候触发了什么集成）
 * - 失败重试（status + retry_count + next_retry_at）
 * - 问题排查（payload + error_message）
 * <p>
 * 参考 OpenProject 的 Webhooks::Log 模型。
 */
@Data
@TableName("external_event_log")
public class ExternalEventLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 适配器类型（如 "email", "sug", "webhook"） */
    private String adapterType;

    /** 事件类型（如 "issue.created", "issue.status_changed"） */
    private String eventType;

    /** 方向：outbound / inbound */
    private String direction;

    /** 关联的业务实体 ID（如 issueKey、projectId 等） */
    private String referenceId;

    /** 事件载荷 JSON */
    @TableField(typeHandler = com.trackflow.common.handler.JsonbTypeHandler.class)
    private String payload;

    /** 处理状态：pending / processing / success / failed / cancelled */
    private String status;

    /** 失败时的错误消息 */
    private String errorMessage;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

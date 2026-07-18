package com.trackflow.external.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方集成事件日志 VO
 */
@Data
public class IntegrationLogVO {

    private String id;

    /** 适配器类型 */
    private String adapterType;

    /** 事件类型 */
    private String eventType;

    /** 方向：outbound / inbound */
    private String direction;

    /** 关联业务 ID */
    private String referenceId;

    /** 事件载荷（JSON 字符串） */
    private String payload;

    /** 状态：pending / processing / success / failed / cancelled */
    private String status;

    /** 错误消息 */
    private String errorMessage;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

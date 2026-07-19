package com.trackflow.integration.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发件箱 VO（管理员视图）
 */
@Data
public class NotificationOutboxVO {

    /** 记录 ID */
    private String id;

    /** 事件类型 */
    private String eventType;

    /** 事件参数 JSON */
    private String payload;

    /** 状态：pending / failed / completed */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最后一次失败的错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}

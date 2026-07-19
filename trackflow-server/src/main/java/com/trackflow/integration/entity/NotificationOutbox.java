package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知发件箱实体：存储异步通知发送失败后的待重试记录。
 * <p>
 * 当 NotificationHelper 的 @Async 方法发生异常时，将失败事件写入此表。
 * NotificationRetryScheduler 定时扫描 pending 记录并重试，超过最大重试次数标记为 failed。
 * 管理员可在通知管理页面查看 failed 记录并手动重试。
 */
@Data
@TableName("notification_outbox")
public class NotificationOutbox implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件类型（如 notifyAssigned, notifyStatusChanged） */
    private String eventType;

    /** 完整事件上下文 JSON（包含重新执行通知所需的全部参数） */
    private String payload;

    /** 状态：pending=待重试, failed=超过最大重试次数, completed=重试成功 */
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

    /** 完成时间（重试成功时填入） */
    private LocalDateTime completedAt;

    // ==================== 状态常量 ====================

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_COMPLETED = "completed";
}

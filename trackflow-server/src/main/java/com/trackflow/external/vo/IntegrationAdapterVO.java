package com.trackflow.external.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第三方集成适配器 VO（管理页面列表展示）
 */
@Data
public class IntegrationAdapterVO {

    /** 适配器类型标识（如 "email", "sug", "migration"） */
    private String adapterType;

    /** 适配器显示名称 */
    private String displayName;

    /** 是否启用 */
    private boolean enabled;

    /** 该适配器支持的事件类型列表 */
    private List<String> supportedEvents;

    /** 最大重试次数 */
    private int maxRetries;

    /** 重试间隔（秒） */
    private int retryIntervalSeconds;

    /** 最近一次活动时间（最近日志的 created_at） */
    private LocalDateTime lastActivityAt;

    /** 成功事件数（总计） */
    private long successCount;

    /** 失败事件数（总计） */
    private long failedCount;
}

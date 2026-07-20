package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知订阅规则实体。
 * 用户可基于标签或保存搜索创建订阅，当匹配的工单发生变更时自动通知。
 * 对标 YouTrack Notifications → Subscriptions。
 */
@Data
@TableName("notification_subscription")
public class NotificationSubscription implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 订阅显示名称 */
    private String name;

    /** 来源类型：tag / saved_query / builtin */
    private String sourceType;

    /** 来源 ID：tag_id 或 saved_query_id */
    private Long sourceId;

    /** 内建订阅标识：assigned_to_me / reported_by_me / commented_by_me */
    private String builtinKey;

    /** 是否为系统默认订阅 */
    private Boolean isDefault;

    /** 事件配置 JSON */
    private String events;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

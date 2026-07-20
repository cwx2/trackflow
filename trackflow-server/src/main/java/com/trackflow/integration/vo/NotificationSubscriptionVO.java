package com.trackflow.integration.vo;

import lombok.Data;

/**
 * 通知订阅 VO（返回给前端）
 */
@Data
public class NotificationSubscriptionVO {

    private String id;
    private String userId;
    private String name;

    /** 来源类型：tag / saved_query / builtin */
    private String sourceType;

    /** 来源 ID */
    private String sourceId;

    /** 来源名称（标签名/保存搜索名），前端展示用 */
    private String sourceName;

    /** 内建订阅标识 */
    private String builtinKey;

    /** 是否默认订阅（不可删除） */
    private Boolean isDefault;

    /** 事件配置 */
    private SubscriptionEventsVO events;

    private String createdAt;
    private String updatedAt;
}

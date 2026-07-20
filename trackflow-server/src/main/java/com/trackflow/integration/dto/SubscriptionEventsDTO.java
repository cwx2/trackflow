package com.trackflow.integration.dto;

import lombok.Data;

/**
 * 订阅事件配置 DTO（输入）
 */
@Data
public class SubscriptionEventsDTO {

    private Boolean onCreated;
    private Boolean onUpdated;
    private Boolean onResolved;
    private Boolean onCommented;
    private Boolean onTagAdded;
    private Boolean onTagRemoved;
}

package com.trackflow.integration.vo;

import lombok.Data;

/**
 * 订阅事件配置 VO
 */
@Data
public class SubscriptionEventsVO {

    private Boolean onCreated = true;
    private Boolean onUpdated = true;
    private Boolean onResolved = true;
    private Boolean onCommented = true;
    private Boolean onTagAdded = true;
    private Boolean onTagRemoved = true;
}

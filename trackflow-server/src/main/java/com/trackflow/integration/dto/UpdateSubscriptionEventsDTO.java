package com.trackflow.integration.dto;

import lombok.Data;

/**
 * 更新订阅事件配置 DTO
 */
@Data
public class UpdateSubscriptionEventsDTO {

    /** 事件配置 */
    private SubscriptionEventsDTO events;
}

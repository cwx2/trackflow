package com.trackflow.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建通知订阅 DTO
 */
@Data
public class CreateSubscriptionDTO {

    /** 订阅来源类型 */
    @NotBlank(message = "来源类型不能为空")
    @Pattern(regexp = "tag|saved_query|project", message = "来源类型必须是 tag、saved_query 或 project")
    private String sourceType;

    /** 来源 ID（标签ID 或 保存搜索ID） */
    @NotBlank(message = "来源ID不能为空")
    private String sourceId;

    /** 事件配置（可选，不传则使用默认全开） */
    private SubscriptionEventsDTO events;
}

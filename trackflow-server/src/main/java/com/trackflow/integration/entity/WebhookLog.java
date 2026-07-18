package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "webhook_log", autoResultMap = true)
public class WebhookLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long webhookId;
    private String event;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;

    private Integer responseStatus;
    private String responseBody;
    private Boolean success;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

package com.trackflow.integration.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "webhook", autoResultMap = true)
public class Webhook implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String name;
    private String url;
    private String secret;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String events;   // JSON array: ["issue.created", "issue.status_changed"]

    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

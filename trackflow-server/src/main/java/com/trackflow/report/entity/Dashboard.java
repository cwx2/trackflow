package com.trackflow.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义仪表盘实体
 */
@Data
@TableName(value = "dashboard", autoResultMap = true)
public class Dashboard implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private Boolean shared;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String layout;

    /** Optimistic lock version for layout updates */
    private Integer layoutVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

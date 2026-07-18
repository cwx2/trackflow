package com.trackflow.quickaction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快捷动作定义
 */
@Data
@TableName(value = "quick_action_definition", autoResultMap = true)
public class QuickActionDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String actionKey;

    private String label;

    private String icon;

    private Integer sortOrder;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String formSchema;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actions;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String visibility;

    private String statusTransitionTo;

    private Boolean enabled;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.trackflow.automation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 工作流实体
 */
@Data
@TableName(value = "automation_workflow", autoResultMap = true)
public class AutomationWorkflow implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 工作流画布定义（JSON：nodes + edges + variables） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String definition;

    private Long projectId;
    private String status;
    private Integer version;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String publishedDefinition;
    private String triggerType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String triggerConfig;
    private String concurrencyMode;
    private Integer maxConcurrent;
    private Long actorUserId;
    private LocalDateTime publishedAt;
    private Boolean runtimeEnabled;
    private LocalDateTime activatedAt;
    private Long activatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

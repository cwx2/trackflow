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

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

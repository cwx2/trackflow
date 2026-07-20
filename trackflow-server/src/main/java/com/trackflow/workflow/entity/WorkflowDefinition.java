package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流定义实体（命名的工作流容器）。
 * <p>
 * 对标 YouTrack 的命名 Workflow 概念——
 * 一个工作流定义包含一组转换规则，可以被附加（attach）到多个项目。
 */
@Data
@TableName("workflow_definition")
public class WorkflowDefinition implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作流名称 */
    private String name;

    /** 工作流描述 */
    private String description;

    /** 是否为系统默认工作流（新项目自动绑定） */
    private Boolean isDefault;

    /** 创建者 */
    private Long createdBy;

    /** 最后更新者 */
    private Long updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

package com.trackflow.automation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自动化工作流模板实体
 */
@Data
@TableName("automation_workflow_template")
public class AutomationWorkflowTemplate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 分类：ai_task, notification, issue_management */
    private String category;

    /** 模板图标（emoji） */
    private String icon;

    /** 工作流定义 JSON（与 AutomationWorkflow.definition 格式相同） */
    private String definition;

    /** 排序权重 */
    private Integer sortOrder;

    /** 是否为内置模板（内置模板不允许删除） */
    private Boolean isBuiltin;

    private LocalDateTime createdAt;
}

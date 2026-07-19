package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流自动化规则实体 —— 字段变更时自动执行动作。
 */
@Data
@TableName(value = "workflow_rule", autoResultMap = true)
public class WorkflowRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目 ID，NULL 表示全局规则 */
    private Long projectId;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /** 规则类型：on_change */
    private String ruleType;

    /** 触发事件：issue_created / field_changed */
    private String triggerEvent;

    /** 触发字段名（field_changed 时可指定），NULL=所有字段 */
    private String triggerField;

    /** 前置条件 JSON 数组 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String conditionJson;

    /** 执行动作 JSON 数组 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actionJson;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序字段 */
    private Integer sortOrder;

    /** 创建者 */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

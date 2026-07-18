package com.trackflow.rule.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则定义实体
 */
@Data
@TableName(value = "rule_definition", autoResultMap = true)
public class RuleDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long projectId;

    private Boolean enabled;

    /** 触发类型: scheduled / event / manual */
    private String triggerType;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String triggerConfig;

    /** 计算公式: linear_daily / fixed / cumulative_increment / custom */
    private String scoreFormula;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String scoreConfig;

    /** 作用对象字段: assignee / reporter / created_by */
    private String targetField;

    /** 定时规则的 cron 表达式 */
    private String scheduleCron;

    /** 去重策略: daily / once_per_issue / no_dedup */
    private String dedupStrategy;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actions;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

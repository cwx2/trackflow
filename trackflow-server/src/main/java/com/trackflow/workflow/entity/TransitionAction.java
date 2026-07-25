package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 转换动作实体 —— 绑定在状态转换路径上的自动化动作配置。
 * 与 workflow_transition（权限规则）分离，独立管理转换动作。
 */
@Data
@TableName(value = "transition_action", autoResultMap = true)
public class TransitionAction implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目 ID，NULL 表示全局默认 */
    private Long projectId;

    /** Issue 类型：Bug/Task/Feature/* (通配符表示所有类型) */
    private String issueType;

    /** 转换前状态 ID */
    private Long oldStatusId;

    /** 转换后状态 ID */
    private Long newStatusId;

    /** 触发类型：transition(精确路径) / on_enter(进入状态) / on_exit(离开状态) */
    private String triggerType;

    /** 动作类型：auto_assign (可扩展) */
    private String actionType;

    /** 策略配置 JSON（JSONB 存储） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actionConfig;

    /** 执行顺序（同路径多动作按此排序） */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建者用户 ID */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

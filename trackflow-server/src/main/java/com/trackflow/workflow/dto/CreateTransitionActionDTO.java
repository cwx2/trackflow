package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 创建转换动作 DTO
 */
@Data
public class CreateTransitionActionDTO {

    /** 项目 ID，0 表示全局默认 */
    @NotNull
    private Long projectId;

    /** Issue 类型，"*" 表示所有类型 */
    @NotBlank
    private String issueType;

    /** 触发类型：transition(精确路径) / on_enter(进入状态) / on_exit(离开状态) */
    @NotBlank
    private String triggerType;

    /** 转换前状态 ID，null 表示"创建时触发"（transition类型）或"进入状态触发"（on_enter类型） */
    private Long oldStatusId;

    /** 转换后状态 ID */
    @NotNull
    private Long newStatusId;

    /** 动作类型，如 "auto_assign" */
    @NotBlank
    private String actionType;

    /** 动作配置（JSON 结构） */
    @NotNull
    private Map<String, Object> actionConfig;

    /** 执行顺序，默认 0 */
    private Integer sortOrder;

    /** 是否启用，默认 true */
    private Boolean enabled;
}

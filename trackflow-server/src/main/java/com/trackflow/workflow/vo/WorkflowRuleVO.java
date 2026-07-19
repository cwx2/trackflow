package com.trackflow.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流规则 VO（返回前端）
 */
@Data
public class WorkflowRuleVO {

    private String id;

    /** 关联项目 ID，null=全局 */
    private String projectId;

    private String name;

    private String description;

    private String ruleType;

    private String triggerEvent;

    private String triggerField;

    /** 前置条件 JSON 数组 */
    private String conditionJson;

    /** 执行动作 JSON 数组 */
    private String actionJson;

    private Boolean enabled;

    private Integer sortOrder;

    /** 调度表达式（on_schedule 类型） */
    private String cronExpression;

    /** 上次执行时间（on_schedule 类型） */
    private LocalDateTime lastExecutedAt;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

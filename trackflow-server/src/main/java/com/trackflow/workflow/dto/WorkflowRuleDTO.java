package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新工作流规则 DTO
 */
@Data
public class WorkflowRuleDTO {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称不能超过200字符")
    private String name;

    private String description;

    /** 规则类型：on_change / on_schedule / action */
    private String ruleType;

    /** 触发事件：on_change 时必填（issue_created / field_changed / comment_added），on_schedule/action 时忽略 */
    private String triggerEvent;

    /** 触发字段名，NULL=所有字段 */
    private String triggerField;

    /** 前置条件 JSON 数组字符串 */
    @NotNull(message = "条件不能为空")
    private String conditionJson;

    /** 执行动作 JSON 数组字符串 */
    @NotNull(message = "动作不能为空")
    private String actionJson;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 排序字段 */
    private Integer sortOrder;

    /** 调度表达式：daily / weekly / hourly 或标准 cron（仅 on_schedule） */
    private String cronExpression;

    /** Action Rule 的命令名（仅 action 类型）：全局唯一，小写字母+数字+连字符 */
    @Size(max = 100, message = "命令名不能超过100字符")
    private String actionCommand;
}

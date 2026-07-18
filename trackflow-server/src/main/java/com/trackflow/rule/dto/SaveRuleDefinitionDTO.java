package com.trackflow.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新规则 DTO
 */
@Data
public class SaveRuleDefinitionDTO {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称最长200字符")
    private String name;

    private String description;

    private Long projectId;

    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    @NotNull(message = "触发条件不能为空")
    private String triggerConfig;

    @NotBlank(message = "计算公式不能为空")
    private String scoreFormula;

    @NotNull(message = "计算配置不能为空")
    private String scoreConfig;

    private String targetField = "assignee";

    private String scheduleCron;

    private String dedupStrategy = "daily";

    private String actions;
}

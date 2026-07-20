package com.trackflow.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新看板图表配置 DTO。
 */
@Data
public class UpdateBoardChartConfigDTO {

    /** 图表类型: burndown / cumulative_flow */
    @NotBlank(message = "图表类型不能为空")
    @Pattern(regexp = "burndown|cumulative_flow", message = "图表类型只能是 burndown 或 cumulative_flow")
    private String chartType;

    /** Burndown 计算方式: issue_count / estimation / work_items */
    @NotBlank(message = "计算方式不能为空")
    @Pattern(regexp = "issue_count|estimation|work_items", message = "计算方式只能是 issue_count、estimation 或 work_items")
    private String burndownCalculation;

    /** Issue 过滤器模式: all_cards / custom */
    @NotBlank(message = "过滤模式不能为空")
    @Pattern(regexp = "all_cards|custom", message = "过滤模式只能是 all_cards 或 custom")
    private String issueFilterMode;

    /** 自定义过滤条件（当 issueFilterMode='custom' 时可用） */
    private String issueFilterQuery;

    /** 当前估算字段 ID */
    private Long estimationFieldId;

    /** 原始估算字段 ID（用于 Burndown 偏差计算） */
    private Long originalEstimationFieldId;
}

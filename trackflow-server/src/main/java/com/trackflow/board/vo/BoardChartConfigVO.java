package com.trackflow.board.vo;

import lombok.Data;

/**
 * 看板图表配置 VO。
 */
@Data
public class BoardChartConfigVO {

    /** 图表类型: burndown / cumulative_flow */
    private String chartType;

    /** Burndown 计算方式: issue_count / estimation / work_items */
    private String burndownCalculation;

    /** Issue 过滤器模式: all_cards / custom */
    private String issueFilterMode;

    /** 自定义过滤条件 */
    private String issueFilterQuery;

    /** 当前估算字段 ID */
    private String estimationFieldId;

    /** 原始估算字段 ID */
    private String originalEstimationFieldId;
}

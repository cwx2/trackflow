package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 看板列聚合查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectBoardColumnAggregation — CTE 合并 issue_count + total_estimation + in_workflow。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class ColumnAggregationRow {
    private Long statusId;
    private Integer issueCount;
    private BigDecimal totalEstimation;
    private Boolean inWorkflow;
}

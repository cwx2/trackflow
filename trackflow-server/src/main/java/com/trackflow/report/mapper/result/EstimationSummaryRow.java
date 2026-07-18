package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 预估对比报表聚合汇总结果行
 * SQL 列：project_name, estimated_hours_sum, spent_hours_sum, issue_count
 */
@Data
public class EstimationSummaryRow {
    private String projectName;
    private Double estimatedHoursSum;
    private Double spentHoursSum;
    private Integer issueCount;
}

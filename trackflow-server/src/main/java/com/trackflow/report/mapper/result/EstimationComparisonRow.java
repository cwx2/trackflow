package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 预估对比查询结果行
 * SQL 列：issue_id, issue_key, title, project_name, assignee_name, estimated_hours, spent_hours
 */
@Data
public class EstimationComparisonRow {
    private Long issueId;
    private String issueKey;
    private String title;
    private String projectName;
    private String assigneeName;
    private Double estimatedHours;
    private Double spentHours;
}

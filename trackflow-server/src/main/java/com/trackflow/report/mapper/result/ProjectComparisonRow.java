package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 跨项目对比查询结果行
 * SQL 列：project_id, project_name, project_key, total, open_count, closed_count, overdue
 */
@Data
public class ProjectComparisonRow {
    private Long projectId;
    private String projectName;
    private String projectKey;
    private Long total;
    private Long openCount;
    private Long closedCount;
    private Long overdue;
}

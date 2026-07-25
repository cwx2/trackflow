package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 按工单分组工时查询结果行
 * SQL 列：issue_id, issue_key, title, project_name, status_name, total_minutes, entry_count
 */
@Data
public class TimeIssueGroupRow {
    private Long issueId;
    private String issueKey;
    private String title;
    private String projectName;
    private String statusName;
    private Integer totalMinutes;
    private Integer entryCount;
}

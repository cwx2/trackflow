package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 工时记录明细查询结果行（Per Work Item 视图）
 * SQL 列：entry_id, work_date, user_name, issue_key, issue_title, project_name, work_type, minutes, description
 */
@Data
public class TimeWorkItemRow {
    private Long entryId;
    private String workDate;
    private String userName;
    private String issueKey;
    private String issueTitle;
    private String projectName;
    private String workType;
    private Integer minutes;
    private String description;
}

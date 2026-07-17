package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 按项目分组工时查询结果行
 * SQL 列：project_id, project_name, total_minutes
 */
@Data
public class TimeByProjectRow {
    private Long projectId;
    private String projectName;
    private Integer totalMinutes;
}

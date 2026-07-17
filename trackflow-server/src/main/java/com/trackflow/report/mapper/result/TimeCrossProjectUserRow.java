package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 交叉维度（项目×人员）工时查询结果行
 * SQL 列：project_name, user_name, total_minutes
 */
@Data
public class TimeCrossProjectUserRow {
    private String projectName;
    private String userName;
    private Integer totalMinutes;
}

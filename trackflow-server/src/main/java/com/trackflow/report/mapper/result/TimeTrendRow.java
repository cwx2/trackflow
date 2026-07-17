package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 每日工时趋势查询结果行
 * SQL 列：work_date, total_minutes
 */
@Data
public class TimeTrendRow {
    private String workDate;
    private Integer totalMinutes;
}

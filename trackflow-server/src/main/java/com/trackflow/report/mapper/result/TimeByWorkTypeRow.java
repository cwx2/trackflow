package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 按工作类型分组工时查询结果行
 * SQL 列：work_type, total_minutes
 */
@Data
public class TimeByWorkTypeRow {
    private String workType;
    private Integer totalMinutes;
}

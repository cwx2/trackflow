package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 解决时间分组明细查询结果行
 * SQL 列：group_name, avg_hours, median_hours, cnt
 */
@Data
public class ResolutionTimeGroupRow {
    private String groupName;
    private Double avgHours;
    private Double medianHours;
    private Long cnt;
}

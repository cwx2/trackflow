package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 解决时间趋势查询结果行
 * SQL 列：period, avg_hours, median_hours, p90_hours, resolved_count
 */
@Data
public class ResolutionTimeTrendRow {
    private String period;
    private Double avgHours;
    private Double medianHours;
    private Double p90Hours;
    private Long resolvedCount;
}

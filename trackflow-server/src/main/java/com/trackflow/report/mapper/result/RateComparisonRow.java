package com.trackflow.report.mapper.result;

import lombok.Data;

import java.time.LocalDate;

/**
 * 比率对比报表查询结果行
 * SQL 列：day, cnt
 * 用于 Fixed vs Reported / Verified vs Reopened / Resolved vs New 等双线对比图
 */
@Data
public class RateComparisonRow {
    private LocalDate day;
    private Long cnt;
}

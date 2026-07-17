package com.trackflow.report.mapper.result;

import lombok.Data;

import java.time.LocalDate;

/**
 * 趋势查询结果行（创建趋势/解决趋势共用）
 * SQL 列：day, cnt
 */
@Data
public class TrendRow {
    private LocalDate day;
    private Long cnt;
}

package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 报表执行引擎 — 通用分组统计行
 * 用于 executeReport 的灵活分组查询
 * SQL 列：label, cnt
 */
@Data
public class ReportGroupRow {
    private String label;
    private Long cnt;
}

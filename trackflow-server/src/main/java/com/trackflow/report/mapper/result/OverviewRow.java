package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 概览统计查询结果行
 * SQL 列：total, open_count, closed_count, unassigned, overdue
 */
@Data
public class OverviewRow {
    private Long total;
    private Long openCount;
    private Long closedCount;
    private Long unassigned;
    private Long overdue;
}

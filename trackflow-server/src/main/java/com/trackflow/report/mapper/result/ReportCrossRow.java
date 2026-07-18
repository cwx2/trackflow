package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 报表双维度交叉统计行
 * SQL 列：primary_label, secondary_label, cnt
 */
@Data
public class ReportCrossRow {
    private String primaryLabel;
    private String secondaryLabel;
    private Long cnt;
}

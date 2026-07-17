package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 累积流图查询结果行
 * SQL 列：day, status_name, status_color, sort_order, cnt
 */
@Data
public class CumulativeFlowRow {
    private String day;
    private String statusName;
    private String statusColor;
    private Integer sortOrder;
    private Long cnt;
}

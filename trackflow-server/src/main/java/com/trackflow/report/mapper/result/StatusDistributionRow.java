package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 状态分布查询结果行
 * SQL 列：status_name, status_color, status_category, sort_order, cnt
 */
@Data
public class StatusDistributionRow {
    private String statusName;
    private String statusColor;
    private String statusCategory;
    private Integer sortOrder;
    private Long cnt;
}

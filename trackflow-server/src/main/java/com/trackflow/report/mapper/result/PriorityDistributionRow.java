package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 优先级分布查询结果行
 * SQL 列：priority_name, cnt
 */
@Data
public class PriorityDistributionRow {
    private String priorityName;
    private Long cnt;
}

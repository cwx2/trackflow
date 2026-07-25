package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工单估时批量查询的强类型结果行。
 * <p>
 * 对应 SQL: selectEstimatedHoursByIds — 批量获取工单的 estimated_hours。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class IssueEstimatedHoursRow {
    private Long id;
    private BigDecimal estimatedHours;
}

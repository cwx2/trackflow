package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单创建时间批量查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectCreatedAtByIds — 批量获取工单的 created_at。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class IssueCreatedAtRow {
    private Long id;
    private LocalDateTime createdAt;
}

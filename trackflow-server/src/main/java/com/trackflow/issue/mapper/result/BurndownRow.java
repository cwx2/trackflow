package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 燃尽图投影查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectBurndownProjection — 只返回 id, created_at, resolved_at（性能优化）。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class BurndownRow {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}

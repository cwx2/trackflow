package com.trackflow.issue.mapper.result;

import lombok.Data;

/**
 * 过期回收站工单 ID 查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectExpiredTrash — 查询超过保留期限的已删除工单。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class ExpiredTrashRow {
    private Long id;
}

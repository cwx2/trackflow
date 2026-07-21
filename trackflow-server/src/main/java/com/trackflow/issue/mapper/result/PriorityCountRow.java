package com.trackflow.issue.mapper.result;

import lombok.Data;

/**
 * 优先级工单数量统计的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectIssueCountByPriority — 查询项目中各优先级的工单数量。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class PriorityCountRow {
    private String priority;
    private Integer cnt;
}

package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectTrashPage — 查询已删除工单列表 + 删除人名称。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class TrashRow {
    private Long id;
    private Long projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String priority;
    private LocalDateTime deletedAt;

    // JOIN sys_user (assignee)
    private String assigneeName;

    // JOIN sys_user (deleted_by via activity)
    private String deletedByName;
}

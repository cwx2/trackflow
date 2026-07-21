package com.trackflow.issue.mapper.result;

import lombok.Data;

/**
 * 子任务查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectChildrenByParentId — 查询指定父工单的所有子任务（带状态和负责人名称）。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class ChildIssueRow {
    private Long id;
    private String issueKey;
    private String title;
    private String issueType;
    private String priority;

    // JOIN issue_status
    private String statusName;
    private String statusColor;
    private String statusCategory;

    // JOIN sys_user (assignee)
    private String assigneeName;
}

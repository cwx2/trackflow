package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 忽略逻辑删除过滤的工单查询结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectByIdIgnoreDeleted — 用于恢复操作和永久删除操作时读取已删除工单信息。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class DeletedIssueRow {
    private Long id;
    private Long projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String priority;
    private LocalDateTime deletedAt;
    private Long assigneeId;
    private Long reporterId;
    private Long sprintId;
    private Long statusId;
    private Long parentId;
}

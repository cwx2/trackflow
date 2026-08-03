package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Issue 详情 JOIN 查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectDetailById — 一次 JOIN 查出 Issue 及其关联名称。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class IssueDetailRow {
    // issue 表字段
    private Long id;
    private Long projectId;
    private String issueKey;
    private String title;
    private String description;
    private String issueType;
    private Long statusId;
    private String priority;
    private Long assigneeId;
    private Long reporterId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private BigDecimal spentHours;
    private BigDecimal derivedEstimatedHours;
    private BigDecimal derivedSpentHours;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    // JOIN project
    private String projectName;
    private String projectStatus;

    // JOIN issue_status
    private String statusName;
    private String statusCode;
    private String statusColor;
    private String statusCategory;
    private Boolean statusIsDefault;
    private Boolean statusIsClosed;
    private String statusDisplayName;

    // JOIN sys_user (assignee)
    private String assigneeName;
    private String assigneeAvatarUrl;

    // JOIN sys_user (reporter)
    private String reporterName;

    // JOIN sys_user (creator)
    private Long createdById;
    private String createdByName;

    // JOIN sys_user (updater)
    private Long updatedById;
    private String updatedByName;

    // JOIN sprint
    private String sprintName;
    private String sprintStatus;

    // JOIN issue (parent)
    private String parentKey;

    // issue.visibility
    private String visibility;
}

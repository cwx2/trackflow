package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 看板卡片查询结果行 — 单次 JOIN 查出卡片渲染所需的所有字段。
 */
@Data
public class BoardCardRow {
    private Long id;
    private Long projectId;
    /** 项目 Key（如 DE4、APP），多项目看板时用于区分来源项目 */
    private String projectKey;
    private String issueKey;
    private String title;
    private String issueType;
    private Long statusId;
    private String statusName;
    private String statusColor;
    private String priority;
    private Long priorityOptionId;
    private Long issueTypeOptionId;
    private Long assigneeId;
    private String assigneeName;
    private String assigneeAvatarUrl;
    private Long sprintId;
    private String sprintName;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Integer childCount;
    private Integer childClosedCount;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    /** 父工单 ID（用于 Issues 类型泳道分组） */
    private Long parentId;
    /** 父工单 issue key */
    private String parentIssueKey;
    /** 父工单标题 */
    private String parentTitle;
}

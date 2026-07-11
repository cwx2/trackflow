package com.trackflow.issue.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class IssueDetailVO {
    private String id;
    private String projectId;
    private String projectName;
    private String issueKey;
    private String title;
    private String description;
    private String issueType;
    private String statusId;
    private IssueStatusVO status;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String reporterId;
    private String reporterName;
    private String sprintId;
    private String sprintName;
    private String parentId;
    private String parentKey;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private BigDecimal spentHours;
    private String customFields;
    private List<IssueTagVO> tags;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

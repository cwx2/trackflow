package com.trackflow.issue.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class IssueVO {
    private String id;
    private String projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String statusId;
    private String priority;
    private String assigneeId;
    private String assigneeName;
    private String reporterId;
    private String sprintId;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}

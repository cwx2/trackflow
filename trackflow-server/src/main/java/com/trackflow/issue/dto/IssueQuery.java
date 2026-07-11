package com.trackflow.issue.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IssueQuery extends PageQuery {
    private Long projectId;
    private String statusId;       // Supports single ID or comma-separated IDs
    private String priority;       // Supports single value or comma-separated values
    private String assigneeId;     // Supports single ID or comma-separated IDs
    private Long reporterId;
    private String sprintId;       // Supports single ID or comma-separated IDs
    private String issueType;      // Supports single value or comma-separated values
    private String keyword;

    // Negative filters (for "is not" / "none of" operators)
    private String statusIdNot;
    private String priorityNot;
    private String assigneeIdNot;
    private String sprintIdNot;
    private String issueTypeNot;
}

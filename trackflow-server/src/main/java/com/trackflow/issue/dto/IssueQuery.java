package com.trackflow.issue.dto;

import com.trackflow.common.model.PageQuery;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    // Negative filters (for "is not" / "none of" operators)
    private String statusIdNot;
    private String priorityNot;
    private String assigneeIdNot;
    private String sprintIdNot;
    private String issueTypeNot;

    // Special filters
    private String overdue;   // "true" = due_date < today AND status not done
    private String dueSoon;   // "true" = due_date <= today+7 AND status not done
    private String reportedByMe;  // "true" = reporter_id = current user AND status not done
    private String hideResolved;  // "true" = exclude issues with is_closed=true statuses
}

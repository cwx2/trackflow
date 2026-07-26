package com.trackflow.issue.dto;

import com.trackflow.common.model.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class IssueQuery extends PageQuery {
    private Long projectId;
    private String statusId;       // Supports single ID or comma-separated IDs
    private String priority;       // Supports single value or comma-separated values
    private String assigneeId;     // Supports single ID or comma-separated IDs
    private String assigneeName;   // Filter by assignee display name (exact match via sub-query)
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

    // Tag filter: single tag ID or comma-separated tag IDs (OR semantics)
    private String tagId;

    // Parent/child relationship filters
    private Long parentId;         // Filter by parent issue ID (show children of this parent)
    private String hasParent;      // "true" = only sub-tasks, "false" = only top-level issues

    // Date range filters (ISO date format yyyy-MM-dd)
    private LocalDate createdAfter;
    private LocalDate createdBefore;
    private LocalDate updatedAfter;
    private LocalDate updatedBefore;
    private LocalDate resolvedAfter;
    private LocalDate resolvedBefore;

    // Due date range filters (ISO date format yyyy-MM-dd)
    private LocalDate dueAfter;    // due_date >= dueAfter
    private LocalDate dueBefore;   // due_date <= dueBefore

    // Special filters
    private String overdue;   // "true" = due_date < today AND status not done
    private String dueSoon;   // "true" = due_date <= today+7 AND status not done
    private String reportedByMe;  // "true" = reporter_id = current user AND status not done
    private String hideResolved;  // "true" = exclude issues with is_closed=true statuses

    /**
     * 排除在此日期之前已完成的工单（ISO 日期格式 yyyy-MM-dd）。
     * 逻辑：status 不属于 done/cancelled 的全部返回；属于 done/cancelled 的，
     * 仅在 resolved_at >= excludeDoneBefore（或 resolved_at IS NULL）时返回。
     * 典型场景：看板"已完成工单保留天数"服务端过滤。
     */
    private LocalDate excludeDoneBefore;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of(
                "id", "issue_key", "title", "status_id", "priority",
                "assignee_id", "reporter_id", "created_at", "updated_at",
                "due_date", "sprint_id", "issue_type", "project_id",
                "vote_count",
                "estimated_hours", "spent_hours", "remaining"
        );
    }
}

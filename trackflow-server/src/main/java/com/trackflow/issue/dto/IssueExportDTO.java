package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 工单导出请求参数
 */
@Data
public class IssueExportDTO {

    /** 导出格式: xlsx / csv */
    @NotBlank(message = "导出格式不能为空")
    private String format;

    /** 指定工单 ID 列表（选中导出模式），为空时使用筛选条件导出 */
    @Size(max = 500, message = "单次最多导出500条工单")
    private List<Long> issueIds;

    // ========== 筛选条件（复用 IssueQuery 同样的参数） ==========
    private Long projectId;
    private String statusId;
    private String priority;
    private String assigneeId;
    private Long reporterId;
    private String sprintId;
    private String sprintStatus;
    private String issueType;
    private String keyword;

    // Negative filters
    private String statusIdNot;
    private String priorityNot;
    private String assigneeIdNot;
    private String sprintIdNot;
    private String issueTypeNot;

    // Tag & parent filters
    private String tagId;
    private Long parentId;
    private String hasParent;

    // Date range filters (ISO date format yyyy-MM-dd)
    private String createdAfter;
    private String createdBefore;
    private String updatedAfter;
    private String updatedBefore;

    // Special filters
    private String overdue;
    private String dueSoon;
    private String reportedByMe;
    private String assignedToMe;
    private String hideResolved;
    private String dueAfter;   // due_date >= dueAfter (yyyy-MM-dd)
    private String dueBefore;  // due_date <= dueBefore (yyyy-MM-dd)

    /**
     * 将导出 DTO 中的筛选条件转换为 IssueQuery，以便复用统一的筛选引擎。
     * 日期字段从 String（yyyy-MM-dd）转换为 LocalDate；不支持的字段（如 resolvedAfter/resolvedBefore）保持 null。
     */
    public IssueQuery toIssueQuery() {
        IssueQuery q = new IssueQuery();
        q.setProjectId(this.projectId);
        q.setStatusId(this.statusId);
        q.setPriority(this.priority);
        q.setAssigneeId(this.assigneeId);
        q.setReporterId(this.reporterId);
        q.setSprintId(this.sprintId);
        q.setSprintStatus(this.sprintStatus);
        q.setIssueType(this.issueType);
        q.setKeyword(this.keyword);
        q.setStatusIdNot(this.statusIdNot);
        q.setPriorityNot(this.priorityNot);
        q.setAssigneeIdNot(this.assigneeIdNot);
        q.setSprintIdNot(this.sprintIdNot);
        q.setIssueTypeNot(this.issueTypeNot);
        q.setTagId(this.tagId);
        q.setParentId(this.parentId);
        q.setHasParent(this.hasParent);
        q.setOverdue(this.overdue);
        q.setDueSoon(this.dueSoon);
        q.setReportedByMe(this.reportedByMe);
        q.setAssignedToMe(this.assignedToMe);
        q.setHideResolved(this.hideResolved);
        // Date range: parse String → LocalDate
        if (this.createdAfter != null && !this.createdAfter.isBlank()) {
            q.setCreatedAfter(LocalDate.parse(this.createdAfter));
        }
        if (this.createdBefore != null && !this.createdBefore.isBlank()) {
            q.setCreatedBefore(LocalDate.parse(this.createdBefore));
        }
        if (this.updatedAfter != null && !this.updatedAfter.isBlank()) {
            q.setUpdatedAfter(LocalDate.parse(this.updatedAfter));
        }
        if (this.updatedBefore != null && !this.updatedBefore.isBlank()) {
            q.setUpdatedBefore(LocalDate.parse(this.updatedBefore));
        }
        if (this.dueAfter != null && !this.dueAfter.isBlank()) {
            q.setDueAfter(LocalDate.parse(this.dueAfter));
        }
        if (this.dueBefore != null && !this.dueBefore.isBlank()) {
            q.setDueBefore(LocalDate.parse(this.dueBefore));
        }
        return q;
    }
}

package com.trackflow.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
    private String issueType;
    private String keyword;

    // Negative filters
    private String statusIdNot;
    private String priorityNot;
    private String assigneeIdNot;
    private String sprintIdNot;
    private String issueTypeNot;

    // Special filters
    private String overdue;
    private String dueSoon;
    private String reportedByMe;
    private String hideResolved;
}

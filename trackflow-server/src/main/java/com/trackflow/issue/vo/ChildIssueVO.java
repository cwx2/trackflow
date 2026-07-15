package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 子任务简要信息 VO（用于父工单详情中展示子任务列表）
 */
@Data
public class ChildIssueVO {
    private String id;
    private String issueKey;
    private String title;
    private String issueType;
    private String priority;
    private String statusName;
    private String statusColor;
    private String statusCategory;
    private String assigneeName;
}

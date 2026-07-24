package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 相似工单 VO — 用于创建工单时的相似工单提示，仅包含必要展示字段。
 */
@Data
public class SimilarIssueVO {
    private String id;
    private String issueKey;
    private String title;
    private String statusName;
    private String statusColor;
    private String assigneeName;
}

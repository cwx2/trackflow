package com.trackflow.issue.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站工单 VO — 展示已删除工单的摘要信息
 */
@Data
public class IssueTrashVO {
    private String id;
    private String projectId;
    private String issueKey;
    private String title;
    private String issueType;
    private String priority;
    private String assigneeName;
    private LocalDateTime deletedAt;
    private String deletedByName;
}

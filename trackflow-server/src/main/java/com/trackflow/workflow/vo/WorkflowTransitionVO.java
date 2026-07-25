package com.trackflow.workflow.vo;

import lombok.Data;

@Data
public class WorkflowTransitionVO {
    private String id;
    private String projectId;
    private String issueType;
    private String roleId;
    private String oldStatusId;
    private String newStatusId;
    private Boolean author;
    private Boolean assignee;
    private Boolean requireComment;
}

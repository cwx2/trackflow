package com.trackflow.issue.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IssueQuery extends PageQuery {
    private Long projectId;
    private Long statusId;
    private String priority;
    private Long assigneeId;
    private Long reporterId;
    private Long sprintId;
    private String issueType;
    private String keyword;
}

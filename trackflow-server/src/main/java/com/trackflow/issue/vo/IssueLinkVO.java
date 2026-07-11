package com.trackflow.issue.vo;

import lombok.Data;

@Data
public class IssueLinkVO {
    private String id;
    private String linkType;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private IssueStatusVO issueStatus;
}

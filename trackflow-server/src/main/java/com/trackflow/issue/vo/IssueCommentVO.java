package com.trackflow.issue.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IssueCommentVO {
    private String id;
    private String issueId;
    private String userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String source;
    private Boolean isEdited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

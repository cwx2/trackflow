package com.trackflow.dashboard.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DashboardActivityVO {
    private String id;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private String userId;
    private String userName;
    private String userAvatar;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}

package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 工单关注者 VO，展示关注者基本信息
 */
@Data
public class IssueWatcherVO {
    private String userId;
    private String displayName;
    private String username;
    private String avatarUrl;
}

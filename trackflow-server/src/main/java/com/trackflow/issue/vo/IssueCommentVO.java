package com.trackflow.issue.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 软删除时间。非空表示评论已被删除（前端应显示占位符）。
     */
    private LocalDateTime deletedAt;

    /**
     * 可见性限制的组 ID 列表（String 类型防 JS 精度丢失）。
     * null 或空表示全体可见。
     */
    private List<String> visibleToGroupIds;

    /**
     * 可见性限制的组名称列表（前端展示用）。
     */
    private List<String> visibleToGroupNames;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

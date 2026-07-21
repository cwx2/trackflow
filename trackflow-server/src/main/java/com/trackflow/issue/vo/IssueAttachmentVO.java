package com.trackflow.issue.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class IssueAttachmentVO {
    private String id;
    private String issueId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String uploadedBy;
    private LocalDateTime createdAt;

    /**
     * 是否为私有附件（有可见性限制）
     */
    private Boolean isPrivate;

    /**
     * 可见性限制的组 ID 列表（仅对有编辑权限的用户返回）
     */
    private List<String> visibleToGroupIds;

    /**
     * 可见性限制的组名称列表（便于前端展示）
     */
    private List<String> visibleToGroupNames;
}

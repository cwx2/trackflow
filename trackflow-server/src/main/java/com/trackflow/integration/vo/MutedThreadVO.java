package com.trackflow.integration.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已静音线程 VO（返回给前端）
 */
@Data
public class MutedThreadVO {
    private String id;
    private String userId;
    private String resourceType;
    private String resourceId;
    /** 资源标题（如工单的 issueKey + title） */
    private String resourceTitle;
    private LocalDateTime createdAt;
}

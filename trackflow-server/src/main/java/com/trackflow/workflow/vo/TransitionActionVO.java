package com.trackflow.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 转换动作 VO（返回前端展示）
 */
@Data
public class TransitionActionVO {

    private String id;
    private String projectId;
    private String issueType;
    private String oldStatusId;
    private String newStatusId;
    private String oldStatusName;
    private String newStatusName;
    private String actionType;
    private Map<String, Object> actionConfig;
    private Integer sortOrder;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

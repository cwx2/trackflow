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
    /** 该动作绑定的转换路径在当前工作流中是否有效（true=路径存在, false=路径已被删除） */
    private Boolean pathValid;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.trackflow.automation.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流列表 VO（不含 definition 详情）
 */
@Data
public class WorkflowVO {

    private String id;
    private String name;
    private String description;
    private String projectId;
    private String status;
    private Integer version;
    private String triggerType;
    private Boolean runtimeEnabled;
    private LocalDateTime activatedAt;
    private String activatedBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.trackflow.automation.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流详情 VO（含 definition）
 */
@Data
public class WorkflowDetailVO {

    private String id;
    private String name;
    private String description;
    /** 工作流画布定义（JSON 字符串） */
    private String definition;
    private Long projectId;
    private String status;
    private Integer version;
    private String publishedDefinition;
    private String triggerType;
    private String triggerConfig;
    private String concurrencyMode;
    private Integer maxConcurrent;
    private Long actorUserId;
    private LocalDateTime publishedAt;
    private Boolean runtimeEnabled;
    private LocalDateTime activatedAt;
    private String activatedBy;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

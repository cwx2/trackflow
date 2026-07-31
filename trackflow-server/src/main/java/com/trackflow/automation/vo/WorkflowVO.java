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
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

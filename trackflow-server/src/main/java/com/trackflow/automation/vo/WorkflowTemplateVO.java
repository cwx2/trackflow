package com.trackflow.automation.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流模板 VO
 */
@Data
public class WorkflowTemplateVO {

    private String id;
    private String name;
    private String description;
    private String category;
    private String icon;
    private String definition;
    private Integer sortOrder;
    private Boolean isBuiltin;
    private String createdBy;
    private LocalDateTime createdAt;
}

package com.trackflow.project.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectVO {
    private String id;          // Long→String
    private String name;
    private String key;
    private String description;
    private String orgId;       // Long→String
    private String leadId;      // Long→String
    private String status;
    private Integer issueSequence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

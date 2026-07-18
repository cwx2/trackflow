package com.trackflow.report.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReportDefinitionVO {
    private String id;
    private String name;
    private String projectId;
    private String type;
    private String config;
    private Boolean shared;
    private Boolean isSystem;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

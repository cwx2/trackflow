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
    /** 精细化共享对象数量 */
    private Integer shareCount;
    /** 当前用户是否收藏了该报表 */
    private Boolean favorited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

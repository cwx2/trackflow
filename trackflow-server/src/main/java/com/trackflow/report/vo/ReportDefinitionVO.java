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
    /** 报表创建者显示名称（用于共享报表列表中展示归属者） */
    private String ownerDisplayName;
    /** 精细化共享对象数量 */
    private Integer shareCount;
    /** 当前用户是否收藏了该报表 */
    private Boolean favorited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.trackflow.issuetemplate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单模板实体
 */
@Data
@TableName("issue_template")
public class IssueTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private String description;

    private String issueType;

    private String priority;

    /** JSON 数组: ["tagId1","tagId2"] */
    private String defaultTags;

    private Boolean isSystem;

    private Integer sortOrder;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Boolean deleted;
}

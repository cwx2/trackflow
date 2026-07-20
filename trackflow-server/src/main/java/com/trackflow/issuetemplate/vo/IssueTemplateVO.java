package com.trackflow.issuetemplate.vo;

import lombok.Data;

/**
 * 工单模板 VO（返回给前端）
 */
@Data
public class IssueTemplateVO {

    private String id;

    private String projectId;

    private String name;

    private String description;

    private String issueType;

    private String priority;

    /** JSON 数组字符串: ["tagId1","tagId2"] */
    private String defaultTags;

    private Boolean isSystem;

    private Integer sortOrder;

    private String createdBy;
}

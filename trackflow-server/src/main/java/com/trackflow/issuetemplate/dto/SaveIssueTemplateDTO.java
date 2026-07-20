package com.trackflow.issuetemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新工单模板 DTO
 */
@Data
public class SaveIssueTemplateDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称不能超过200字符")
    private String name;

    private String description;

    private String issueType;

    private String priority;

    /** JSON 数组字符串: ["tagId1","tagId2"] */
    private String defaultTags;

    private Integer sortOrder;
}

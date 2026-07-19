package com.trackflow.project.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectDTO {

    @Size(max = 200, message = "项目名称不能超过200字符")
    private String name;

    @Size(max = 2000, message = "描述不能超过2000字符")
    private String description;

    private Long leadId;

    @Pattern(regexp = "^(private|internal|public)$", message = "可见性无效，可选值：private、internal、public")
    private String visibility;

    /**
     * 项目标识（Key）——创建后不可修改。
     * 此字段用于接收请求中的 key 值，以便后端检测并明确拒绝变更请求。
     */
    private String key;
}

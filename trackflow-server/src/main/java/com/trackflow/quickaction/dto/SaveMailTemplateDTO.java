package com.trackflow.quickaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建/更新邮件模板 DTO
 */
@Data
public class SaveMailTemplateDTO {

    private Long projectId;

    @NotBlank(message = "关联动作不能为空")
    private String actionKey;

    @NotBlank(message = "模板名称不能为空")
    private String name;

    @NotBlank(message = "邮件主题模板不能为空")
    private String subjectTemplate;

    @NotBlank(message = "邮件正文模板不能为空")
    private String bodyTemplate;

    private String recipientsRule;

    private Integer sortOrder;

    private Boolean enabled;
}

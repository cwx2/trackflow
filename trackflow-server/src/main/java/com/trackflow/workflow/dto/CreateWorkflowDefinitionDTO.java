package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工作流定义 DTO
 */
@Data
public class CreateWorkflowDefinitionDTO {

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 100, message = "工作流名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /** 是否设为系统默认工作流 */
    private Boolean isDefault;
}

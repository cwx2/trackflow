package com.trackflow.automation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工作流 DTO
 */
@Data
public class CreateWorkflowDTO {

    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 200, message = "工作流名称不能超过200字")
    private String name;

    @Size(max = 2000, message = "描述不能超过2000字")
    private String description;

    private Long projectId;
}

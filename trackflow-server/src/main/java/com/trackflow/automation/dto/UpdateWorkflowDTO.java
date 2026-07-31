package com.trackflow.automation.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新工作流 DTO
 */
@Data
public class UpdateWorkflowDTO {

    @Size(max = 200, message = "工作流名称不能超过200字")
    private String name;

    @Size(max = 2000, message = "描述不能超过2000字")
    private String description;

    /** 工作流画布定义（JSON 字符串） */
    private String definition;
}

package com.trackflow.automation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 从已有工作流保存为模板的请求 DTO
 */
@Data
public class CreateTemplateFromWorkflowDTO {

    /** 源工作流 ID */
    @NotNull(message = "工作流ID不能为空")
    private Long workflowId;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过100字")
    private String name;

    /** 模板描述 */
    @Size(max = 2000, message = "模板描述不能超过2000字")
    private String description;

    /** 分类：ai_task / notification / issue_management / custom */
    private String category;

    /** 模板图标（emoji） */
    @Size(max = 20, message = "图标不能超过20字")
    private String icon;
}

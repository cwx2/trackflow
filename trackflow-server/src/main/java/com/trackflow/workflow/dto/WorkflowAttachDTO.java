package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作流附加/分离操作 DTO
 */
@Data
public class WorkflowAttachDTO {

    @NotNull(message = "工作流定义ID不能为空")
    private Long workflowDefinitionId;
}

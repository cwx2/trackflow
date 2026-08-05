package com.trackflow.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 工作流规则导入请求 DTO（解析 JSON 文件后的结构）。
 */
@Data
public class WorkflowRuleImportDTO {

    /** 导入策略：skip（跳过同名）/ overwrite（覆盖同名） */
    private String conflictStrategy = "skip";

    /** 要导入的规则列表（从 JSON 文件解析而来） */
    private List<WorkflowRuleExportDTO.RuleItem> rules;
}

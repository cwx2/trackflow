package com.trackflow.workflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流规则导入结果 VO
 */
@Data
public class WorkflowRuleImportResultVO {

    /** 成功导入的数量 */
    private int importedCount;

    /** 跳过的数量（名称冲突且策略为 skip） */
    private int skippedCount;

    /** 覆盖的数量（名称冲突且策略为 overwrite） */
    private int overwrittenCount;

    /** 导入的规则名称列表 */
    private List<String> importedRules = new ArrayList<>();

    /** 跳过的规则名称列表 */
    private List<String> skippedRules = new ArrayList<>();

    /** 错误信息列表 */
    private List<String> errors = new ArrayList<>();
}

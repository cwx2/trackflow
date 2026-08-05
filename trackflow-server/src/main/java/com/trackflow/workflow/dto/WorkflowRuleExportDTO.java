package com.trackflow.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流规则导出/导入格式。
 * 不含 projectId、id 等项目绑定信息，用于跨项目复用。
 */
@Data
public class WorkflowRuleExportDTO {

    /** 导出格式版本 */
    private String version = "1.0";

    /** 导出时间 */
    private LocalDateTime exportedAt;

    /** 导出的规则列表 */
    private List<RuleItem> rules;

    @Data
    public static class RuleItem {
        private String name;
        private String description;
        private String ruleType;
        private String triggerEvent;
        private String triggerField;
        private String conditionJson;
        private String actionJson;
        private Integer sortOrder;
        private String cronExpression;
        private String actionCommand;
    }
}

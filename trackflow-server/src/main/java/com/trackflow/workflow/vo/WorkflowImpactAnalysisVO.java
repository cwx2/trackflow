package com.trackflow.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工作流影响分析结果 VO。
 * 返回每个被影响状态的工单计数，供前端展示影响范围。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowImpactAnalysisVO {

    /**
     * 状态 ID → 处于该状态的工单数量。
     * Key 为 String（前端兼容），Value 为工单数。
     */
    private Map<String, Long> statusIssueCounts;

    /**
     * 总影响工单数（所有状态的合计）
     */
    private long totalAffectedIssues;
}

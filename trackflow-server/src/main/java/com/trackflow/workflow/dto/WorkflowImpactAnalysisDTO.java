package com.trackflow.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 工作流影响分析请求 DTO。
 * 前端在保存工作流之前，先提交被删除转换的源状态 ID 列表，
 * 后端返回各状态当前的工单数量，帮助管理员评估变更影响。
 */
@Data
public class WorkflowImpactAnalysisDTO {

    /**
     * 被删除转换的源状态 ID 列表。
     * 前端只需传入那些将要失去出路的状态 ID。
     */
    @NotEmpty(message = "状态 ID 列表不能为空")
    private List<Long> statusIds;

    /**
     * 项目 ID。
     * null/0 表示全局工作流，统计所有未删除工单。
     * >0 表示项目级工作流，仅统计该项目内的工单。
     */
    private Long projectId;

    /**
     * 工单类型筛选（可选）。
     * 如果用户选择了特定类型（非 "*"），则只统计该类型的工单。
     */
    private String issueType;
}

package com.trackflow.sprint.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Sprint 完成操作的返回结果 VO
 * 包含完成统计信息，用于前端展示友好的完成提示
 */
@Data
@Builder
public class SprintCompleteResultVO {

    /** 完成的 Sprint 基础信息 */
    private SprintVO sprint;

    /** Sprint 中工单总数（完成操作时的快照） */
    private int totalIssues;

    /** 已完成工单数（is_closed = true） */
    private int completedIssues;

    /** 未完成工单数（移走或回 Backlog 的工单） */
    private int unresolvedIssues;

    /** 未完成工单的处理方式："backlog" 或 "next_sprint" */
    private String moveOption;

    /** 如果移入其他迭代，目标迭代名称 */
    private String targetSprintName;
}

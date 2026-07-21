package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * Sprint 负责人工作量分布 VO
 */
@Data
public class SprintAssigneeDistributionVO {
    /** Sprint ID */
    private String sprintId;
    /** Sprint 名称 */
    private String sprintName;
    /** 工单总数 */
    private int totalIssues;
    /** 未分配负责人的工单数 */
    private int unassignedCount;
    /** 所有工单预估工时总和 */
    private double totalEstimatedHours;
    /** 未分配工单的预估工时总和 */
    private double unassignedEstimatedHours;
    /** 按负责人分组的工单统计列表（按数量降序） */
    private List<AssigneeItem> assignees;

    @Data
    public static class AssigneeItem {
        /** 用户 ID */
        private String userId;
        /** 显示名称 */
        private String displayName;
        /** 分配的工单总数 */
        private int issueCount;
        /** 已完成工单数 */
        private int doneCount;
        /** 进行中工单数 */
        private int inProgressCount;
        /** 待办工单数 */
        private int todoCount;
        /** 该负责人承担的预估工时总和 */
        private double estimatedHoursTotal;
    }
}

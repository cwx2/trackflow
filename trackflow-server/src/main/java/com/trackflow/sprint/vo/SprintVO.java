package com.trackflow.sprint.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SprintVO {
    private String id;
    private String projectId;
    private String name;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    // ===== 状态推导 =====
    /**
     * 状态提示信息：当 Sprint 的 status 与日期存在矛盾时提供警告。
     * 为 null 时表示状态与日期一致，无异常。
     * 示例值："已超过结束日期，建议尽快完成迭代" / "开始日期尚未到达"
     */
    private String statusHint;

    /**
     * 是否已超期（active 且 end_date < today）
     */
    private boolean overdue;

    // ===== Issue 统计 =====
    /** 工单总数 */
    private int totalIssues;
    /** 已完成工单数（is_closed = true 的状态） */
    private int doneIssues;
    /** 进行中工单数（category = in_progress） */
    private int inProgressIssues;
    /** 待办工单数（category = open） */
    private int todoIssues;
    /** 有逾期工单（due_date < today 且 is_closed = false） */
    private int overdueIssues;
    /** 未分配负责人的工单数（assignee_id IS NULL） */
    private int unassignedIssues;

    // ===== 工时统计 =====
    /** 该 Sprint 所有工单的预估总工时（estimated_hours 之和） */
    private double totalEstimatedHours;
    /** 该 Sprint 已完成工单的预估工时总和 */
    private double completedEstimatedHours;
}

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
}

package com.trackflow.dashboard.vo;

import lombok.Data;

@Data
public class DashboardSummaryVO {
    /** 分配给我的待处理 */
    private long assignedOpen;
    /** 分配给我的进行中 */
    private long assignedInProgress;
    /** 本周已完成 */
    private long completedThisWeek;
    /** 即将到期（7天内） */
    private long dueSoon;
    /** 逾期未完成 */
    private long overdue;
    /** 我报告的未解决 */
    private long reportedByMeOpen;
    /** 总工单数 */
    private long totalIssues;
    /** 活跃项目数 */
    private long activeProjects;
}

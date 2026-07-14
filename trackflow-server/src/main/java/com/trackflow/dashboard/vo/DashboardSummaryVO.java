package com.trackflow.dashboard.vo;

import lombok.Data;

@Data
public class DashboardSummaryVO {
    /** 分配给我的待处理 */
    private long assignedOpen;
    /** 分配给我的进行中 */
    private long assignedInProgress;
    /** 本周已完成（我完成的 + 分配给我已完成的） */
    private long completedThisWeek;
    /** 即将到期（7天内） */
    private long dueSoon;
    /** 逾期未完成 */
    private long overdue;
    /** 我报告的未解决 */
    private long reportedByMeOpen;
    /** 待测试工单数（Testing 状态，我所在项目范围内） */
    private long testingCount;
    /** 总工单数 */
    private long totalIssues;
    /** 活跃项目数 */
    private long activeProjects;
    /** 用户在项目中的主要角色代码（如 tester, developer 等） */
    private String primaryRoleCode;
}

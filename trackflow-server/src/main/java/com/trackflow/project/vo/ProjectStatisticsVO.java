package com.trackflow.project.vo;

import lombok.Data;

import java.util.List;

/**
 * 项目概览统计数据 VO
 */
@Data
public class ProjectStatisticsVO {

    /** 工单总数（不含软删除） */
    private int totalIssues;

    /** 未解决工单数（状态未关闭） */
    private int openIssues;

    /** 已关闭工单数（Done + Cancelled 等） */
    private int closedIssues;

    /** 完成率（百分比，保留1位小数） */
    private double completionRate;

    /** 本周新建工单数 */
    private int createdThisWeek;

    /** 本周关闭工单数 */
    private int closedThisWeek;

    /** 各状态分布 */
    private List<StatusDistribution> statusDistribution;

    /** 当前活跃 Sprint 信息（可能为 null） */
    private ActiveSprintInfo activeSprint;

    @Data
    public static class StatusDistribution {
        private String statusId;
        private String statusName;
        private String statusColor;
        private String category;
        private boolean closed;
        private int count;
    }

    @Data
    public static class ActiveSprintInfo {
        private String id;
        private String name;
        private String startDate;
        private String endDate;
        /** Sprint 中的总工单数 */
        private int totalIssues;
        /** Sprint 中已关闭的工单数 */
        private int completedIssues;
        /** Sprint 剩余天数 */
        private int remainingDays;
    }
}

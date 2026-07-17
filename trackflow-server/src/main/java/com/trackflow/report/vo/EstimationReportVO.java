package com.trackflow.report.vo;

import lombok.Data;

import java.util.List;

/**
 * 预估对比报表 VO — 对比预估工时 vs 实际花费
 */
@Data
public class EstimationReportVO {

    /** 总预估小时数 */
    private double totalEstimatedHours;

    /** 总实际花费小时数 */
    private double totalSpentHours;

    /** 总体偏差比率（spent/estimated - 1，正值=超时，负值=提前） */
    private double overallDeviationRate;

    /** 每个工单的预估对比 */
    private List<IssueEstimationItem> items;

    /** 按项目汇总 */
    private List<ProjectEstimationItem> byProject;

    @Data
    public static class IssueEstimationItem {
        private String issueId;
        private String issueKey;
        private String issueTitle;
        private String projectName;
        private String assigneeName;
        /** 预估小时 */
        private double estimatedHours;
        /** 实际花费小时 */
        private double spentHours;
        /** 偏差率 */
        private double deviationRate;
        /** 偏差方向: over / under / on_track */
        private String deviation;
    }

    @Data
    public static class ProjectEstimationItem {
        private String projectName;
        private double estimatedHours;
        private double spentHours;
        private double deviationRate;
        private int issueCount;
    }
}

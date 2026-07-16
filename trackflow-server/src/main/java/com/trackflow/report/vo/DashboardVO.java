package com.trackflow.report.vo;

import lombok.Data;

/**
 * 仪表盘全量数据 VO
 */
@Data
public class DashboardVO {
    private StatusDistributionVO statusDistribution;
    private PriorityDistributionVO priorityDistribution;
    private TypeDistributionVO typeDistribution;
    private WorkloadVO workload;
    private TrendVO trend;
    private BurndownVO burndown;
    private OverviewVO overview;
    /** 跨项目对比数据（仅"全部项目"模式下返回） */
    private ProjectComparisonVO projectComparison;
}

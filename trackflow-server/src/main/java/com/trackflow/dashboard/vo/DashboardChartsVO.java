package com.trackflow.dashboard.vo;

import lombok.Data;
import java.util.List;

/**
 * 工作台图表数据 VO — 包含趋势折线图、状态分布、团队工作负载
 */
@Data
public class DashboardChartsVO {

    /** 工单趋势（近 14 天） */
    private TrendSection trend;

    /** 状态分布 */
    private StatusDistributionSection statusDistribution;

    /** 团队工作负载 */
    private WorkloadSection workload;

    // ─── 内部结构 ─────────────────────────────────────────

    @Data
    public static class TrendSection {
        /** 日期列表 (yyyy-MM-dd) */
        private List<String> dates;
        /** 每日新建数 */
        private List<Long> created;
        /** 每日关闭数 */
        private List<Long> resolved;
    }

    @Data
    public static class StatusDistributionSection {
        private List<StatusItem> items;
        private long total;
    }

    @Data
    public static class StatusItem {
        private String name;
        private long value;
        private String color;
        private String category;
    }

    @Data
    public static class WorkloadSection {
        private List<WorkloadItem> items;
        private long total;
    }

    @Data
    public static class WorkloadItem {
        private String name;
        private long total;
        private long done;
        private long inProgress;
    }
}

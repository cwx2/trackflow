package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

/**
 * 解决时间分析 VO
 * 展示工单从创建到关闭的平均/中位/P90 耗时随时间的变化趋势
 */
@Data
public class ResolutionTimeVO {
    /** 日期序列（按周聚合时为周起始日） */
    private List<String> dates;
    /** 每个时间段的平均解决时间（小时） */
    private List<Double> avgHours;
    /** 每个时间段的中位解决时间（小时） */
    private List<Double> medianHours;
    /** 每个时间段的 P90 解决时间（小时） */
    private List<Double> p90Hours;
    /** 每个时间段解决的工单数 */
    private List<Long> resolvedCount;
    /** 分组明细（按类型/优先级/负责人） */
    private List<GroupDetail> groupDetails;

    @Data
    public static class GroupDetail {
        /** 分组名称（如 "Bug"、"Critical"、"张伟"） */
        private String name;
        /** 该组平均解决时间（小时） */
        private Double avgHours;
        /** 该组中位解决时间（小时） */
        private Double medianHours;
        /** 该组解决的工单数 */
        private Long count;
    }
}

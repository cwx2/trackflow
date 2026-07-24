package com.trackflow.report.vo;

import lombok.Data;
import java.util.List;

/**
 * 平均工单年龄（Average Issue Age）VO
 * 追踪工单在指定状态中的平均停留时间趋势
 */
@Data
public class AverageIssueAgeVO {
    /** 日期序列（按 granularity 聚合） */
    private List<String> dates;
    /** 每个时间段在跟踪状态中的平均停留时间（小时） */
    private List<Double> avgAgeHours;
    /** 滑动平均停留时间（小时），窗口大小由 movingPeriod 控制 */
    private List<Double> movingAvgHours;
    /** 滑动最小值（小时） */
    private List<Double> movingMinHours;
    /** 滑动最大值（小时） */
    private List<Double> movingMaxHours;
    /** 每个时间段从跟踪状态"流出"的工单数量 */
    private List<Long> outflowCount;
    /** 每个时间段仍"停留"在跟踪状态中的工单数量 */
    private List<Long> stayingCount;
    /** 被追踪的状态列表（回显） */
    private List<String> trackedStatuses;
}

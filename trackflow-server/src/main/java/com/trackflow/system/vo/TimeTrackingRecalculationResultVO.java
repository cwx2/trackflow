package com.trackflow.system.vo;

import lombok.Data;

/**
 * 工时重新计算结果 VO
 */
@Data
public class TimeTrackingRecalculationResultVO {

    /** 更新后的时间追踪设置 */
    private TimeTrackingSettingsVO settings;

    /** 是否执行了重新计算 */
    private boolean recalculated;

    /** 重新计算策略（null 表示未执行） */
    private String strategy;

    /** 受影响的工时记录数 */
    private int affectedTimeEntries;

    /** 受影响的 Issue 预估工时数 */
    private int affectedEstimations;

    /** 旧 hoursPerDay 值 */
    private Integer oldHoursPerDay;

    /** 新 hoursPerDay 值 */
    private Integer newHoursPerDay;
}

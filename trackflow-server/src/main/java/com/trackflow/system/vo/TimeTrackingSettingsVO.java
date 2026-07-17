package com.trackflow.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 时间追踪设置 VO
 */
@Data
public class TimeTrackingSettingsVO {

    /** 每日工作小时数（1-24） */
    private Integer hoursPerDay;

    /** 每周工作日列表（1=周一, 7=周日） */
    private List<Integer> workingDays;
}

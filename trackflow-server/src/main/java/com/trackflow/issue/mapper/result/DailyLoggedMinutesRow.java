package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDate;

/**
 * 燃尽图 work_items 模式：按天汇总的工单记录工时行
 * <p>
 * 对应 SQL: selectSprintDailyLoggedMinutes
 */
@Data
public class DailyLoggedMinutesRow {
    /** 工时记录日期 */
    private LocalDate workDate;
    /** 当天记录的总分钟数 */
    private Integer totalMinutes;
}

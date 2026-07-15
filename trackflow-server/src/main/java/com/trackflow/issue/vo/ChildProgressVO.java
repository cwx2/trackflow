package com.trackflow.issue.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 子任务进度汇总 VO
 */
@Data
public class ChildProgressVO {
    /** 子任务总数 */
    private int total;
    /** 已关闭数 */
    private int closed;
    /** 完成百分比（0-100） */
    private int percent;
    /** 子任务 estimated_hours 汇总 */
    private BigDecimal aggregatedEstimate;
    /** 子任务 spent_hours 汇总 */
    private BigDecimal aggregatedSpent;
}

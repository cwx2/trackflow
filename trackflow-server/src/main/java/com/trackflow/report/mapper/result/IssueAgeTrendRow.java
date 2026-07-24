package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * Average Issue Age 趋势行 — Mapper SQL 查询结果映射
 * 每行代表一个时间段（天/周/月）内，工单在跟踪状态中的停留时间统计
 */
@Data
public class IssueAgeTrendRow {
    /** 时间段标识（日期字符串，如 2026-07-10 或 2026-W28） */
    private String period;
    /** 该时间段内，在跟踪状态中的工单平均停留时间（小时） */
    private Double avgAgeHours;
    /** 该时间段从跟踪状态流出的工单数 */
    private Long outflowCount;
    /** 截至该时间段仍停留在跟踪状态中的工单数 */
    private Long stayingCount;
}

package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 状态转换统计行 — Mapper SQL 查询结果映射
 */
@Data
public class StateTransitionRow {
    /** 源状态名称 */
    private String fromStatus;
    /** 目标状态名称 */
    private String toStatus;
    /** 转换次数 */
    private Long transitionCount;
    /** 平均停留时间（小时） */
    private Double avgDurationHours;
}

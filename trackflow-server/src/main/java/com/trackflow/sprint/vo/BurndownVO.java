package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * 燃尽图数据 VO — 支持工单数模式和估时模式
 */
@Data
public class BurndownVO {
    /** Sprint ID */
    private String sprintId;
    /** Sprint 名称 */
    private String sprintName;
    /** X 轴日期列表 (yyyy-MM-dd) */
    private List<String> dates;
    /** 理想线：基于 Sprint 开始时的工单数/工时线性递减 */
    private List<Double> idealLine;
    /** 实际线：每天的实际剩余工单数/工时（scope - resolved，仅到今天） */
    private List<Integer> actualLine;
    /** 范围线：每天的实际工单总数/总工时（追踪 scope change） */
    private List<Integer> scopeLine;
    /** 今天在 dates 数组中的索引（-1 表示不在 Sprint 范围内） */
    private int todayIndex;
    /** Sprint 当前总工单数 */
    private int totalIssues;
    /** Sprint 开始时的工单数（理想线起点） */
    private int startScopeIssues;
    /** 日均完成速率 */
    private Double velocity;
    /** 按当前速率预测的完成日期 (yyyy-MM-dd)，null 表示速率为 0 */
    private String forecastDate;

    // ===== 估时模式字段 =====
    /** Sprint 激活时的总预估工时快照 */
    private Double startScopeHours;
    /** 当前模式: "issue_count" 或 "estimation" */
    private String mode;
}

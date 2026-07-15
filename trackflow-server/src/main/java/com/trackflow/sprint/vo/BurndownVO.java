package com.trackflow.sprint.vo;

import lombok.Data;

import java.util.List;

/**
 * 燃尽图数据 VO
 */
@Data
public class BurndownVO {
    /** Sprint ID */
    private String sprintId;
    /** Sprint 名称 */
    private String sprintName;
    /** X 轴日期列表 (yyyy-MM-dd) */
    private List<String> dates;
    /** 理想线：每天的理想剩余工单数 */
    private List<Double> idealLine;
    /** 实际线：每天的实际剩余工单数 */
    private List<Integer> actualLine;
    /** 今天在 dates 数组中的索引（-1 表示不在 Sprint 范围内） */
    private int todayIndex;
    /** Sprint 总工单数（起始值） */
    private int totalIssues;
    /** 日均完成速率 */
    private Double velocity;
    /** 按当前速率预测的完成日期 (yyyy-MM-dd)，null 表示速率为 0 */
    private String forecastDate;
}

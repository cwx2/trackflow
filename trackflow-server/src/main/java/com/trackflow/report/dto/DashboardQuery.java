package com.trackflow.report.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Dashboard 统计接口查询参数封装
 * <p>
 * 供 ReportStatisticsController.dashboard 使用，替代多个松散的 @RequestParam，
 * 符合"超过 2 个查询参数必须封装 Query 对象"规范。
 */
@Data
public class DashboardQuery {

    /** 项目 ID（可选，不传时返回用户有权限的全部项目聚合数据） */
    private Long projectId;

    /** Sprint ID（可选，用于过滤特定 Sprint 的数据） */
    private Long sprintId;

    /** 统计开始日期（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 统计结束日期（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /**
     * Issue 筛选条件（JSON 数组格式，与工单列表的筛选语法相同）。
     * 可选，不传时不额外过滤工单范围。
     */
    private String filter;
}

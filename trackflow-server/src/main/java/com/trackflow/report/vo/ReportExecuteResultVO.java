package com.trackflow.report.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表执行结果 VO
 * 对应 GET /api/v1/reports/{id}/data 的返回数据
 *
 * 单维度模式：labels + data（一维数组）
 * 双维度模式：labels（主维度标签） + secondLabels（第二维度标签） + matrix（二维数组）
 */
@Data
public class ReportExecuteResultVO {

    /** 报表标题 */
    private String title;

    /** 图表类型（bar_horizontal/bar_vertical/pie/line/number_card/area/stacked_area） */
    private String chartType;

    /** 报表数据类型（来自 ReportType） */
    private String type;

    /** 报表类别：distribution / timeline / state_transition */
    private String category;

    /** 主分组依据（status/priority/type/assignee/project） */
    private String groupBy;

    /** 第二分组依据（可选，双维度交叉时使用） */
    private String secondGroupBy;

    /** 主维度分组标签列表 */
    private List<String> labels;

    /** 各分组的统计数值（单维度模式） */
    private List<Long> data;

    /** 第二维度标签列表（双维度模式） */
    private List<String> secondLabels;

    /**
     * 矩阵数据（双维度模式）
     * matrix[i][j] = 主维度第i项 × 第二维度第j项的计数
     */
    private List<List<Long>> matrix;

    /** 工单总数 */
    private long total;

    // ─── 时间序列数据（Timeline 类报表使用） ─────────────

    /** 日期序列（ISO 格式：2026-07-01） */
    private List<String> dates;

    /** 时间序列数据（多系列） */
    private List<TimeSeriesData> series;

    /** 理想线数据（燃尽图专用） */
    private List<Double> idealLine;

    /** 概览信息（如平均解决时间、总解决数等） */
    private Map<String, Object> summary;

    // ─── 状态转换数据（State Transition 类报表使用） ────

    /** 状态转换记录 */
    private List<StateTransitionItem> transitions;

    // ─── 通用元数据 ──────────────────────────────────────

    /** 应用的筛选条件摘要（方便前端展示） */
    private Map<String, Object> appliedFilters;

    /** 数据计算时间（ISO 格式时间戳） */
    private LocalDateTime calculatedAt;

    /** 自动刷新间隔（秒），null/0 表示不自动刷新 */
    private Integer refreshInterval;

    /**
     * 时间序列数据项
     */
    @Data
    public static class TimeSeriesData {
        /** 系列名称（如 "实际完成"、"平均解决时间"、状态名） */
        private String name;
        /** 系列颜色（可选，前端可覆盖） */
        private String color;
        /** 数据值（与 dates 等长） */
        private List<Number> data;
        /** 系列类型（line/area，默认 line） */
        private String seriesType;
    }

    /**
     * 状态转换统计项
     */
    @Data
    public static class StateTransitionItem {
        /** 源状态 */
        private String fromStatus;
        /** 目标状态 */
        private String toStatus;
        /** 转换次数 */
        private Long count;
        /** 平均停留时间（小时） */
        private Double avgDurationHours;
    }
}

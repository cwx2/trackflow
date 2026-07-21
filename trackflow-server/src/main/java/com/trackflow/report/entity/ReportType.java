package com.trackflow.report.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表类型枚举
 * 定义系统支持的所有报表类型
 */
public enum ReportType {

    // ─── Issue Distribution 类 ─────────────────────
    ISSUE_COUNT("issue_count", "工单数量统计", "status", "distribution"),
    BY_STATUS("by_status", "按状态分布", "status", "distribution"),
    BY_ASSIGNEE("by_assignee", "按负责人分布", "assignee", "distribution"),
    BY_PRIORITY("by_priority", "按优先级分布", "priority", "distribution"),
    BY_TYPE("by_type", "按工单类型分布", "type", "distribution"),
    CUSTOM("custom", "自定义报表", null, "distribution"),

    // ─── Timeline 类（时间线趋势） ──────────────────
    BURNDOWN("burndown", "燃尽图", null, "timeline"),
    BURNDOWN_CHART("burndown_chart", "燃尽图报表", null, "timeline"),
    CUMULATIVE_FLOW("cumulative_flow", "累积流图", null, "timeline"),
    RESOLUTION_TIME("resolution_time", "解决时间分析", null, "timeline"),

    // ─── State Transition 类（状态转换） ─────────────
    STATE_TRANSITION("state_transition", "状态转换统计", null, "state_transition");

    private final String value;
    private final String label;
    /** 该类型对应的默认 groupBy，null 表示由用户自定义 */
    private final String defaultGroupBy;
    /** 报表类别：distribution / timeline / state_transition */
    private final String category;

    private static final Map<String, ReportType> VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ReportType::getValue, t -> t));

    ReportType(String value, String label, String defaultGroupBy, String category) {
        this.value = value;
        this.label = label;
        this.defaultGroupBy = defaultGroupBy;
        this.category = category;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultGroupBy() {
        return defaultGroupBy;
    }

    public String getCategory() {
        return category;
    }

    /**
     * 判断是否为时间线类报表
     */
    public boolean isTimeline() {
        return "timeline".equals(category);
    }

    /**
     * 判断是否为状态转换类报表
     */
    public boolean isStateTransition() {
        return "state_transition".equals(category);
    }

    /**
     * 判断是否为分布类报表（使用 GROUP BY 聚合）
     */
    public boolean isDistribution() {
        return "distribution".equals(category);
    }

    /**
     * 根据字符串值获取枚举实例
     * @return 对应的 ReportType，不存在时返回 null
     */
    public static ReportType fromValue(String value) {
        if (value == null) return null;
        return VALUE_MAP.get(value);
    }

    /**
     * 判断给定值是否为合法的报表类型
     */
    public static boolean isValid(String value) {
        return value != null && VALUE_MAP.containsKey(value);
    }

    /**
     * 获取所有合法的 type 值列表（用于错误提示）
     */
    public static String allowedValues() {
        return VALUE_MAP.keySet().stream().sorted().collect(Collectors.joining(", "));
    }
}

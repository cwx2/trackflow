package com.trackflow.report.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘微件类型枚举。
 * 定义所有合法的 widget_type 值，用于 Service 层校验和 DB CHECK 约束。
 */
public enum WidgetType {

    NOTE("note", "快捷笔记"),
    NUMBER_CARD("number_card", "数字卡片"),
    REPORT_DISTRIBUTION("report_distribution", "分布图表"),
    ISSUE_LIST("issue_list", "Issue 列表"),
    ACTIVITY_FEED("activity_feed", "活动流"),
    REPORT("report", "报表图表"),
    SPRINT_PROGRESS("sprint_progress", "Sprint 进度"),
    CALENDAR("calendar", "到期日历"),
    AGILE_CHART("agile_chart", "敏捷图表"),
    AGILE_BOARD_STATUS("agile_board_status", "看板状态");

    private final String value;
    private final String label;

    private static final Map<String, WidgetType> VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(WidgetType::getValue, t -> t));

    WidgetType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据字符串值获取枚举实例
     * @return 对应的 WidgetType，不存在时返回 null
     */
    public static WidgetType fromValue(String value) {
        if (value == null) return null;
        return VALUE_MAP.get(value);
    }

    /**
     * 判断给定值是否为合法的微件类型
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

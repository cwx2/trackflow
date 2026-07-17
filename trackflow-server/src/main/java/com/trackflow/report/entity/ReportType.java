package com.trackflow.report.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表类型枚举
 * 定义系统支持的所有报表类型
 */
public enum ReportType {

    ISSUE_COUNT("issue_count", "工单数量统计", "status"),
    BY_STATUS("by_status", "按状态分布", "status"),
    BY_ASSIGNEE("by_assignee", "按负责人分布", "assignee"),
    BY_PRIORITY("by_priority", "按优先级分布", "priority"),
    BY_TYPE("by_type", "按工单类型分布", "type"),
    BURNDOWN("burndown", "燃尽图", null),
    CUSTOM("custom", "自定义报表", null);

    private final String value;
    private final String label;
    /** 该类型对应的默认 groupBy，null 表示由用户自定义 */
    private final String defaultGroupBy;

    private static final Map<String, ReportType> VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ReportType::getValue, t -> t));

    ReportType(String value, String label, String defaultGroupBy) {
        this.value = value;
        this.label = label;
        this.defaultGroupBy = defaultGroupBy;
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

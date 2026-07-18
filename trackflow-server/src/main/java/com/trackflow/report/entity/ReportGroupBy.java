package com.trackflow.report.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表分组维度枚举
 * 定义 config.groupBy 支持的所有合法值
 */
public enum ReportGroupBy {

    STATUS("status", "按状态"),
    ASSIGNEE("assignee", "按负责人"),
    PRIORITY("priority", "按优先级"),
    TYPE("type", "按工单类型"),
    PROJECT("project", "按项目");

    private final String value;
    private final String label;

    private static final Set<String> VALID_VALUES = Arrays.stream(values())
            .map(ReportGroupBy::getValue)
            .collect(Collectors.toUnmodifiableSet());

    ReportGroupBy(String value, String label) {
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
     * 判断给定值是否为合法的分组维度
     */
    public static boolean isValid(String value) {
        return value != null && VALID_VALUES.contains(value);
    }

    /**
     * 获取所有合法的 groupBy 值列表（用于错误提示）
     */
    public static String allowedValues() {
        return VALID_VALUES.stream().sorted().collect(Collectors.joining(", "));
    }
}

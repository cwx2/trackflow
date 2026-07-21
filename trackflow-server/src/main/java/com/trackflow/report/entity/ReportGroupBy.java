package com.trackflow.report.entity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报表分组维度枚举 + 自定义字段动态分组支持
 * <p>
 * 内置维度：status/assignee/priority/type/project
 * 自定义字段维度：cf_{fieldId}（如 cf_2077320550062825474）
 */
public enum ReportGroupBy {

    STATUS("status", "按状态"),
    ASSIGNEE("assignee", "按负责人"),
    PRIORITY("priority", "按优先级"),
    TYPE("type", "按工单类型"),
    PROJECT("project", "按项目");

    /** 自定义字段分组维度的前缀 */
    public static final String CUSTOM_FIELD_PREFIX = "cf_";

    private final String value;
    private final String label;

    private static final Set<String> BUILTIN_VALUES = Arrays.stream(values())
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
     * 判断给定值是否为合法的内置分组维度
     */
    public static boolean isBuiltin(String value) {
        return value != null && BUILTIN_VALUES.contains(value);
    }

    /**
     * 判断给定值是否为合法的分组维度（内置 + 自定义字段格式）
     * <p>
     * 自定义字段格式校验仅检查前缀和 ID 数字格式，
     * 实际存在性校验由 Service 层负责。
     */
    public static boolean isValid(String value) {
        if (value == null) return false;
        if (BUILTIN_VALUES.contains(value)) return true;
        return isCustomFieldFormat(value);
    }

    /**
     * 判断是否为自定义字段分组格式（cf_开头 + 数字ID）
     */
    public static boolean isCustomFieldFormat(String value) {
        if (value == null || !value.startsWith(CUSTOM_FIELD_PREFIX)) return false;
        String idPart = value.substring(CUSTOM_FIELD_PREFIX.length());
        if (idPart.isEmpty()) return false;
        try {
            Long.parseLong(idPart);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 从自定义字段分组值中提取字段 ID
     *
     * @param value 如 "cf_2077320550062825474"
     * @return 字段 ID，如果不是自定义字段格式则返回 null
     */
    public static Long extractCustomFieldId(String value) {
        if (!isCustomFieldFormat(value)) return null;
        try {
            return Long.parseLong(value.substring(CUSTOM_FIELD_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取所有内置 groupBy 值列表（用于错误提示）
     */
    public static String allowedValues() {
        return BUILTIN_VALUES.stream().sorted().collect(Collectors.joining(", "))
                + ", cf_{自定义字段ID}";
    }
}

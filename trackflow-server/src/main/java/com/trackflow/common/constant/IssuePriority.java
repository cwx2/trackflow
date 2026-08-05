package com.trackflow.common.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Issue 优先级枚举 — 统一管理优先级值、排序权重和显示颜色。
 *
 * <p>所有需要引用优先级字面量的代码必须使用本枚举，禁止硬编码字符串。</p>
 *
 * <p>注意：数据库中存储的是 {@link #getValue()} 返回的字符串（如 "Critical"），
 * 本枚举的 {@link #fromValue(String)} 方法支持大小写不敏感匹配。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public enum IssuePriority {

    CRITICAL("Critical", 1, "#f85149"),
    HIGH("High", 2, "#d29922"),
    NORMAL("Normal", 3, "#58a6ff"),
    LOW("Low", 4, "#6b7280");

    /** 数据库存储值 */
    private final String value;
    /** 排序权重（数值越小优先级越高） */
    private final int sortOrder;
    /** 显示颜色 */
    private final String color;

    IssuePriority(String value, int sortOrder, String color) {
        this.value = value;
        this.sortOrder = sortOrder;
        this.color = color;
    }

    public String getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getColor() {
        return color;
    }

    /**
     * 默认优先级（Normal）。
     */
    public static final IssuePriority DEFAULT = NORMAL;

    /**
     * 所有优先级值集合（用于参数校验）。
     */
    public static final List<String> ALL_VALUES = Arrays.stream(values())
            .map(IssuePriority::getValue)
            .toList();

    /**
     * 优先级颜色映射（value → color）。
     */
    public static final Map<String, String> COLOR_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(IssuePriority::getValue, IssuePriority::getColor));

    /**
     * 根据数据库存储值查找枚举（大小写不敏感）。
     *
     * @param value 优先级字符串值
     * @return 对应枚举，未匹配返回 null
     */
    public static IssuePriority fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 兼容旧数据：medium 映射为 Normal
        if ("medium".equalsIgnoreCase(value)) {
            return NORMAL;
        }
        for (IssuePriority priority : values()) {
            if (priority.value.equalsIgnoreCase(value)) {
                return priority;
            }
        }
        return null;
    }

    /**
     * 生成 SQL CASE 表达式（用于 ORDER BY 优先级排序）。
     *
     * <p>使用 LOWER() 确保大小写不敏感。数值越小优先级越高。</p>
     *
     * @return SQL CASE 表达式字符串
     */
    public static String sortCaseExpression() {
        return Arrays.stream(values())
                .map(p -> String.format("WHEN '%s' THEN %d", p.value.toLowerCase(), p.sortOrder))
                .collect(Collectors.joining(" ", "CASE LOWER(priority) ", " ELSE 99 END"));
    }
}

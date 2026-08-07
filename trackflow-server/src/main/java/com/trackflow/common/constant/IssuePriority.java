package com.trackflow.common.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issue 优先级枚举 — 统一管理优先级值、排序权重和显示颜色。
 *
 * <p>所有需要引用优先级字面量的代码必须使用本枚举，禁止硬编码字符串。</p>
 *
 * <p>数据库中存储的是中文显示值（如 "紧急"、"高"、"普通"、"低"），
 * 本枚举的 {@link #fromValue(String)} 方法同时支持中文值和历史英文值的匹配。</p>
 *
 * @author TrackFlow
 * @since 1.0
 */
public enum IssuePriority {

    SHOW_STOPPER("阻塞", 0, "#ff1744"),
    CRITICAL("紧急", 1, "#f85149"),
    HIGH("高", 2, "#d29922"),
    NORMAL("普通", 3, "#58a6ff"),
    LOW("低", 4, "#6b7280");

    /** 数据库存储值（中文） */
    private final String value;
    /** 排序权重（数值越小优先级越高） */
    private final int sortOrder;
    /** 显示颜色 */
    private final String color;

    /**
     * 历史英文值 → 枚举的兼容映射（用于 fromValue 向后兼容）
     */
    private static final Map<String, IssuePriority> LEGACY_MAP = Map.of(
            "show-stopper", SHOW_STOPPER,
            "critical", CRITICAL,
            "high", HIGH,
            "normal", NORMAL,
            "medium", NORMAL,
            "low", LOW
    );

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
     * 默认优先级（普通）。
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
     * 根据数据库存储值查找枚举。
     * 支持中文值（当前标准）和历史英文值（向后兼容）。
     *
     * @param value 优先级字符串值
     * @return 对应枚举，未匹配返回 null
     */
    public static IssuePriority fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 优先精确匹配当前中文值
        for (IssuePriority priority : values()) {
            if (priority.value.equals(value)) {
                return priority;
            }
        }
        // 兼容历史英文值（大小写不敏感）
        IssuePriority legacy = LEGACY_MAP.get(value.toLowerCase());
        if (legacy != null) {
            return legacy;
        }
        return null;
    }

    /**
     * 生成 SQL CASE 表达式（用于 ORDER BY 优先级排序）。
     *
     * <p>同时支持中文值（当前）和历史英文值（兼容未迁移数据）。</p>
     *
     * @return SQL CASE 表达式字符串
     */
    public static String sortCaseExpression() {
        StringBuilder sb = new StringBuilder("CASE priority ");
        for (IssuePriority p : values()) {
            sb.append(String.format("WHEN '%s' THEN %d ", p.value, p.sortOrder));
        }
        // 兼容可能残留的历史英文值
        sb.append("WHEN 'Show-stopper' THEN 0 ");
        sb.append("WHEN 'Critical' THEN 1 ");
        sb.append("WHEN 'High' THEN 2 ");
        sb.append("WHEN 'Normal' THEN 3 ");
        sb.append("WHEN 'medium' THEN 3 ");
        sb.append("WHEN 'Low' THEN 4 ");
        sb.append("ELSE 99 END");
        return sb.toString();
    }
}

package com.trackflow.common.constant;

import java.util.Map;

/**
 * Issue 优先级枚举 — 仅保留历史英文值兼容映射，供数据迁移场景使用。
 *
 * <p><b>重要</b>：优先级的值定义、颜色、排序全部来自数据库
 * （{@code custom_field_option} 表），代码中不再使用此枚举定义字段值。</p>
 *
 * <p>本枚举唯一用途：{@link #fromValue(String)} 方法将历史英文值（如 "Critical"）
 * 映射为当前中文值（如 "紧急"），用于数据迁移和向后兼容。</p>
 *
 * @deprecated 仅用于数据迁移兼容，新代码应使用 PriorityFieldService 获取选项
 * @author TrackFlow
 * @since 1.0
 */
public enum IssuePriority {

    SHOW_STOPPER("阻塞", 0),
    CRITICAL("紧急", 1),
    HIGH("高", 2),
    NORMAL("普通", 3),
    LOW("低", 4);

    /** 数据库存储值（中文） */
    private final String value;
    /** 排序权重（仅用于 fromValue 匹配，实际排序以 DB position 为准） */
    private final int sortOrder;

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

    IssuePriority(String value, int sortOrder) {
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public String getValue() {
        return value;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * 默认优先级（普通）— 仅作为最终 fallback，正常路径应从 PriorityFieldService 获取。
     */
    public static final IssuePriority DEFAULT = NORMAL;

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
}

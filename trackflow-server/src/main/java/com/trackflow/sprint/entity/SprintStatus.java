package com.trackflow.sprint.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Sprint 状态枚举。
 * <p>
 * 数据库存储为 VARCHAR(20)，与枚举 value 字段对应。
 * MyBatis-Plus 通过 @EnumValue 自动转换，Jackson 通过 @JsonValue 序列化为字符串。
 */
@Getter
public enum SprintStatus {

    PLANNED("planned", "计划中"),
    ACTIVE("active", "进行中"),
    COMPLETED("completed", "已完成"),
    ARCHIVED("archived", "已归档");

    @EnumValue
    @JsonValue
    private final String value;

    private final String label;

    SprintStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据数据库值解析枚举，null/空字符串返回 PLANNED（默认）。
     */
    public static SprintStatus fromValue(String value) {
        if (value == null || value.isBlank()) return PLANNED;
        for (SprintStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown SprintStatus value: " + value);
    }
}

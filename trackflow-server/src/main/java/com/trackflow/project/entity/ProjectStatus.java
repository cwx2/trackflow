package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 项目状态枚举。
 * <p>
 * 数据库存储为 VARCHAR(20)，与枚举 value 字段对应。
 * MyBatis-Plus 通过 @EnumValue 自动转换，Jackson 通过 @JsonValue 序列化为字符串。
 */
@Getter
public enum ProjectStatus {

    ACTIVE("active", "活跃"),
    ARCHIVED("archived", "已归档");

    @EnumValue
    @JsonValue
    private final String value;

    private final String label;

    ProjectStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据数据库值解析枚举，null/空字符串返回 ACTIVE（默认）。
     */
    public static ProjectStatus fromValue(String value) {
        if (value == null || value.isBlank()) return ACTIVE;
        for (ProjectStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown ProjectStatus value: " + value);
    }
}

package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 项目可见性枚举。
 * <p>
 * 数据库存储为 VARCHAR(20)，与枚举 value 字段对应。
 * MyBatis-Plus 通过 @EnumValue 自动转换，Jackson 通过 @JsonValue 序列化为字符串。
 */
@Getter
public enum ProjectVisibility {

    PRIVATE("private", "私有"),
    INTERNAL("internal", "组织内部"),
    PUBLIC("public", "公开");

    @EnumValue
    @JsonValue
    private final String value;

    private final String label;

    ProjectVisibility(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据数据库值解析枚举，null/空字符串返回 PRIVATE（默认）。
     */
    public static ProjectVisibility fromValue(String value) {
        if (value == null || value.isBlank()) return PRIVATE;
        for (ProjectVisibility v : values()) {
            if (v.value.equalsIgnoreCase(value)) return v;
        }
        throw new IllegalArgumentException("Unknown ProjectVisibility value: " + value);
    }
}

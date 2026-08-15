package com.trackflow.issue.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 批量操作类型枚举
 * <p>
 * 替代 BatchOperationDTO 中的魔法字符串，提供编译期类型安全和 IDE 自动补全。
 * Jackson 通过 {@link #fromValue} 支持 JSON 字符串反序列化（向后兼容）。
 */
public enum BatchOperationType {

    STATUS("status"),
    ASSIGN("assign"),
    SPRINT("sprint"),
    PRIORITY("priority"),
    DELETE("delete"),
    RESTORE("restore"),
    TAG_ADD("tag_add"),
    TAG_REMOVE("tag_remove");

    private final String value;

    BatchOperationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BatchOperationType fromValue(String value) {
        if (value == null) return null;
        for (BatchOperationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的批量操作类型: " + value);
    }
}

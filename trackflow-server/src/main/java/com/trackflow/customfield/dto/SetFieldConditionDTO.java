package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设置字段条件显示 DTO
 * 条件：当 conditionFieldId 字段的值在 conditionValues 中时，本字段才显示
 */
@Data
public class SetFieldConditionDTO {

    /**
     * 条件源字段 ID（必须为枚举类型单值字段，且已附加到同一项目）。
     * 传 null 表示清除条件（始终显示）。
     */
    private Long conditionFieldId;

    /**
     * 触发显示的选项 ID 列表。
     * 为 null 或空列表 + conditionFieldId 为 null = 清除条件。
     */
    private List<String> conditionValues;
}

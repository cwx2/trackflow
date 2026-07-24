package com.trackflow.customfield.dto;

import lombok.Data;

import java.util.List;

/**
 * 设置字段值过滤规则 DTO
 *
 * 值过滤（Filter values based on）：当源字段选择了某个值时，本字段只展示指定的选项子集。
 * 与条件显示（conditionFieldId）是两个独立机制：
 * - 条件显示：控制字段本身是否出现
 * - 值依赖过滤：字段出现，但下拉选项被缩小
 */
@Data
public class SetFieldFilterRulesDTO {

    /**
     * 过滤源字段 ID（必须为枚举类型单值字段，且已附加到同一项目）。
     * 传 null 表示清除过滤规则（显示所有非归档选项）。
     */
    private Long filterFieldId;

    /**
     * 过滤规则列表。
     * 当源字段值为 whenValue 时，本字段只展示 showOnly 中的选项。
     * 为 null 或空列表 + filterFieldId 为 null = 清除过滤规则。
     */
    private List<FilterRuleItem> rules;

    /**
     * 单条过滤规则
     */
    @Data
    public static class FilterRuleItem {
        /**
         * 源字段的选项 ID（当源字段选择此值时触发过滤）
         */
        private String whenValue;

        /**
         * 目标字段允许显示的选项 ID 列表
         */
        private List<String> showOnly;
    }
}

package com.trackflow.customfield.dto;

import lombok.Data;

/**
 * 设置字段在项目中的数字徽章显示配置 DTO。
 * 仅对整数（integer）类型字段有效。
 */
@Data
public class SetBadgeConfigDTO {

    /**
     * 是否在工单列表标题左侧以数字徽章形式展示该字段值。
     */
    private Boolean showAsBadge;

    /**
     * 徽章颜色规则 JSON 数组字符串。
     * 格式: [{"max":1,"color":"#ef4444"},{"max":3,"color":"#f97316"},{"color":"#3b82f6"}]
     * null 或空数组时使用默认蓝色。
     */
    private String badgeColorRules;
}

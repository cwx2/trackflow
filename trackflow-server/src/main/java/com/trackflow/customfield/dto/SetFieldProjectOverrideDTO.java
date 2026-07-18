package com.trackflow.customfield.dto;

import lombok.Data;

/**
 * 设置字段项目级覆盖 DTO（必填性 + 默认值）。
 * null 值表示"继承全局设置"。
 */
@Data
public class SetFieldProjectOverrideDTO {

    /**
     * 项目级必填性覆盖。
     * null = 继承全局设置，true/false = 项目级覆盖。
     */
    private Boolean isRequired;

    /**
     * 项目级默认值覆盖。
     * null = 继承全局设置，空字符串 "" = 显式设为无默认值。
     */
    private String defaultValue;
}

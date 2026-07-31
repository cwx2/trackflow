package com.trackflow.customfield.dto;

import lombok.Data;

/**
 * 设置字段项目级覆盖 DTO（必填性 + 默认值 + 是否允许为空）。
 * null 值表示"继承全局设置"。
 * <p>
 * YouTrack 风格的"无默认值但必填"模式通过 canBeEmpty=false + defaultValue=null 实现。
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

    /**
     * 项目级"是否允许为空"覆盖。
     * null = 继承全局设置（默认 true），false = 不能为空。
     * <p>
     * 当 canBeEmpty=false 且 defaultValue=null/空时，表示"无默认值但必填"模式，
     * 前端会显示 "Set value" 提示（参考 YouTrack "No value (required)"）。
     */
    private Boolean canBeEmpty;
}

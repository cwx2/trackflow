package com.trackflow.customfield.vo;

import lombok.Data;

import java.util.List;

@Data
public class CustomFieldValueVO {
    private String customFieldId;
    private String fieldName;
    private String fieldFormat;

    /** 单值字段的原始值，多值字段为 null */
    private String value;

    /** 多值字段的原始值列表，单值字段为 null */
    private List<String> values;

    /** 单值字段的展示值 */
    private String displayValue;

    /** 多值字段的展示值列表 */
    private List<String> displayValues;

    /** 是否为多值字段 */
    private Boolean isMulti;
}

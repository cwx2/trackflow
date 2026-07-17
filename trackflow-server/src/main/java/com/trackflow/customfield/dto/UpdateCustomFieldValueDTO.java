package com.trackflow.customfield.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新单个自定义字段值的请求体。
 * <p>
 * 单值字段使用 value 字段传入单个字符串。
 * 多值字段（is_multi=true 的 list 类型）使用 values 字段传入数组。
 * 两者互斥：如果 values 非空则使用 values，否则使用 value。
 */
@Data
public class UpdateCustomFieldValueDTO {
    /** 单值字段的值 */
    private String value;

    /** 多值字段的值列表（每个元素为一个选项 ID） */
    private List<String> values;
}

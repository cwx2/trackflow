package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义字段定义实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("custom_field_definition")
public class CustomFieldDefinition extends BaseEntity {

    private String name;

    /** 字段类型: string, int, float, date, bool, list, user */
    private String fieldFormat;

    private Boolean isRequired;

    /** true=全局(所有项目可用), false=需关联到具体项目 */
    private Boolean isForAll;

    private String defaultValue;

    /** 最小长度(仅 string 类型) */
    private Integer minLength;

    /** 最大长度(仅 string 类型) */
    private Integer maxLength;

    /** 正则表达式(仅 string 类型) */
    private String regexp;

    /** 排序位置 */
    private Integer position;
}

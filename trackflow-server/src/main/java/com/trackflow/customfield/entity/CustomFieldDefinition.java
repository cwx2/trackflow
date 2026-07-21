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

    /** 字段类型: string, text, int, float, date, datetime, bool, list, user */
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

    /** 列表类型是否支持多值选择（仅 list 类型有效） */
    private Boolean isMulti;

    /** 是否在工单列表的默认可选列中隐藏（管理员控制默认可见性） */
    private Boolean isHiddenInList;

    /** 字段别名（逗号分隔），用户在搜索和命令中可用别名替代字段全名 */
    private String aliases;

    /** 是否为私有字段（仅拥有对应权限的用户可查看/编辑） */
    private Boolean isPrivate;
}

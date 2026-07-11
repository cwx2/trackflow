package com.trackflow.customfield.vo;

import lombok.Data;

@Data
public class AvailableColumnVO {
    /** 列标识符，如 "assignee" 或 "cf_42" */
    private String key;
    /** 列显示名 */
    private String label;
    /** 分组: standard / custom */
    private String group;
    /** 仅自定义字段列有值: string/int/float/date/bool/list/user */
    private String fieldFormat;
    /** 是否支持排序 */
    private Boolean sortable;
    /** 是否可从列表移除（false = 固定列，如标题） */
    private Boolean removable;
}

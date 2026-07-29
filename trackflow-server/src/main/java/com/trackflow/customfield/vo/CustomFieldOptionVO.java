package com.trackflow.customfield.vo;

import lombok.Data;

@Data
public class CustomFieldOptionVO {
    private String id;
    private String customFieldId;
    private String value;
    private Integer position;
    private Boolean isDefault;
    private Boolean isArchived;
    /** 选项颜色（HEX 格式如 #4CAF50），null 表示无颜色 */
    private String color;
    /** 选项描述，在下拉选择时以 tooltip 形式展示 */
    private String description;
}

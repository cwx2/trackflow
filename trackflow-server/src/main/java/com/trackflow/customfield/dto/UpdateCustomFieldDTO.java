package com.trackflow.customfield.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateCustomFieldDTO {

    @Size(max = 256, message = "字段名称不能超过256个字符")
    private String name;

    // field_format 不可修改

    private Boolean isRequired;
    private Boolean isForAll;
    private String defaultValue;
    private Integer minLength;
    private Integer maxLength;
    private String regexp;

    /** 列表类型是否支持多值选择（仅 list 类型有效） */
    private Boolean isMulti;

    /** 是否在工单列表的默认列选择器中隐藏 */
    private Boolean isHiddenInList;

    /** 字段别名（逗号分隔），用户在搜索和命令中可用别名替代字段全名 */
    @Size(max = 512, message = "别名总长度不能超过512个字符")
    private String aliases;

    /** 是否为私有字段 */
    private Boolean isPrivate;

    /** list 类型的选项（全量替换） */
    private List<OptionItem> options;

    /** 从已有字段追加选项（list 类型有效）。指定源字段 ID，会将其活跃选项追加到当前字段的选项列表中（跳过已存在的同名选项）。 */
    private Long copyOptionsFromFieldId;

    /** 关联的项目 ID 列表（全量替换） */
    private List<Long> projectIds;

    /** 关联的 Issue 类型列表（全量替换） */
    private List<String> issueTypes;

    @Data
    public static class OptionItem {
        private Long id; // 已有选项的 ID，null 表示新增
        @Size(max = 256)
        private String value;
        private Boolean isDefault = false;
        /** 选项颜色（HEX 格式如 #4CAF50），null 表示无颜色 */
        @Size(max = 20)
        private String color;
    }
}

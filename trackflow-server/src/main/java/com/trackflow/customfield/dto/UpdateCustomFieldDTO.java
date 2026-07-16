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

    /** list 类型的选项（全量替换） */
    private List<OptionItem> options;

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
    }
}

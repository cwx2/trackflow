package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateCustomFieldDTO {

    @NotBlank(message = "字段名称不能为空")
    @Size(max = 256, message = "字段名称不能超过256个字符")
    private String name;

    @NotBlank(message = "字段类型不能为空")
    private String fieldFormat;

    private Boolean isRequired = false;
    private Boolean isForAll = false;
    private String defaultValue;
    private Integer minLength = 0;
    private Integer maxLength = 0;
    private String regexp;

    /** 列表类型是否支持多值选择（仅 list 类型有效） */
    private Boolean isMulti = false;

    /** 是否在工单列表的默认列选择器中隐藏 */
    private Boolean isHiddenInList = false;

    /** list 类型的选项值列表 */
    private List<OptionItem> options;

    /** 关联的项目 ID 列表（is_for_all=false 时使用） */
    private List<Long> projectIds;

    /** 关联的 Issue 类型列表 */
    private List<String> issueTypes;

    @Data
    public static class OptionItem {
        @NotBlank(message = "选项值不能为空")
        @Size(max = 256)
        private String value;
        private Boolean isDefault = false;
        /** 选项颜色（HEX 格式如 #4CAF50），null 表示无颜色 */
        @Size(max = 20)
        private String color;
    }
}

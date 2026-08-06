package com.trackflow.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
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

    /** 列表类型是否支持多值选择（仅 list/version 类型有效） */
    private Boolean isMulti = false;

    /** 是否在工单列表的默认列选择器中隐藏 */
    private Boolean isHiddenInList = false;

    /** 字段别名（逗号分隔），用户在搜索和命令中可用别名替代字段全名 */
    @Size(max = 512, message = "别名总长度不能超过512个字符")
    private String aliases;

    /** 是否为私有字段 */
    private Boolean isPrivate = false;

    /** 是否自动附加到新创建的项目 */
    private Boolean isAutoAttach = false;

    /** list 类型的选项值列表 */
    private List<OptionItem> options;

    /** 从已有字段复制选项（list 类型有效）。指定源字段 ID，会复制其所有活跃选项作为新字段的初始值集。 */
    private Long copyOptionsFromFieldId;

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
        /** 选项描述（在下拉选择时展示 tooltip） */
        @Size(max = 1024, message = "选项描述不能超过1024个字符")
        private String description;
        /** 仅 state 类型字段使用：标记该状态值是否视为"已解决" */
        private Boolean isResolved;
        /** 仅 ownedField 类型使用：选项负责人用户 ID */
        private Long ownerUserId;
        /** 仅 version 类型使用：版本发布日期 */
        private LocalDate releaseDate;
        /** 仅 version 类型使用：是否已正式发布 */
        private Boolean isReleased;
        /** 仅 build 类型使用：构建生成日期 */
        private LocalDate assembleDate;
    }
}

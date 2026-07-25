package com.trackflow.customfield.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomFieldDefinitionVO {
    private String id;
    private String name;
    private String fieldFormat;
    private Boolean isRequired;
    private Boolean isForAll;
    private Boolean isMulti;
    private String defaultValue;
    private Integer minLength;
    private Integer maxLength;
    private String regexp;
    private Integer position;
    /** 是否在工单列表中隐藏（管理员设置） */
    private Boolean isHiddenInList;
    /** 字段别名（逗号分隔），用户在搜索和命令中可用别名替代字段全名 */
    private String aliases;
    /** 是否为私有字段（仅拥有对应权限的用户可查看/编辑） */
    private Boolean isPrivate;
    /** 是否自动附加到新创建的项目（YouTrack Auto-attach 行为） */
    private Boolean isAutoAttach;
    /** 选项排序模式: manual / name_asc / name_desc / name_ci_asc / name_ci_desc */
    private String sortMode;
    private List<CustomFieldOptionVO> options;
    private List<String> projectIds;
    private List<String> issueTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 条件显示（项目级别） =====

    /**
     * 条件源字段 ID（项目级配置）。
     * null 表示无条件（始终显示）。
     */
    private String conditionFieldId;

    /**
     * 触发显示的选项 ID 列表。
     */
    private List<String> conditionValues;

    // ===== 基于角色的可见性/可编辑性（项目级别） =====

    /**
     * 可以查看此字段的角色 ID 列表。
     * null = 所有项目成员可见。
     */
    private List<Long> visibleToRoles;

    /**
     * 可以编辑此字段的角色 ID 列表。
     * null = 所有可见用户都能编辑。
     */
    private List<Long> updatableByRoles;

    /**
     * 当前用户是否可以编辑此字段（由后端根据用户角色计算，前端只读使用）。
     * true = 可编辑，false = 只读。
     */
    private Boolean editable;

    // ===== 项目级覆盖（仅项目上下文接口返回） =====

    /**
     * 项目级必填性覆盖。
     * null = 使用全局设置（isRequired），非 null = 项目覆盖值。
     */
    private Boolean projectIsRequired;

    /**
     * 项目级默认值覆盖。
     * null = 使用全局设置（defaultValue），非 null = 项目覆盖值。
     */
    private String projectDefaultValue;

    /**
     * 计算后的有效必填性（考虑项目覆盖后的实际值）。
     * 前端校验时应使用此值。
     */
    private Boolean effectiveIsRequired;

    /**
     * 计算后的有效默认值（考虑项目覆盖后的实际值）。
     */
    private String effectiveDefaultValue;

    // ===== 值依赖过滤（项目级别） =====

    /**
     * 值过滤依赖的源字段 ID。
     * null 表示无值过滤（展示所有非归档选项）。
     * 与条件显示（conditionFieldId）是两个独立机制：
     * - 条件显示：控制字段本身是否出现
     * - 值依赖过滤：字段出现，但下拉选项被缩小
     */
    private String filterFieldId;

    /**
     * 过滤规则。
     * 当源字段值为 whenValue 时，本字段只展示 showOnly 列表中的选项。
     * 格式: [{"whenValue":"optionId1","showOnly":["optionId3","optionId4"]}, ...]
     */
    private String filterRules;
}

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
}

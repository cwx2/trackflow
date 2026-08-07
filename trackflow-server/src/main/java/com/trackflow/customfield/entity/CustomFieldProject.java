package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;

/**
 * 自定义字段与项目的关联
 */
@Data
@TableName(value = "custom_field_project", autoResultMap = true)
public class CustomFieldProject implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customFieldId;

    private Long projectId;

    /** 字段在项目中的显示顺序 */
    private Integer position;

    /**
     * 条件源字段 ID（仅枚举类型单值字段）。
     * 为 NULL 表示无条件，始终显示。
     */
    private Long conditionFieldId;

    /**
     * 触发显示的选项 ID 列表（JSON 数组字符串）。
     * 当条件源字段的当前值在此列表中时，本字段才在工单界面显示。
     * 格式: ["123","456"]
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String conditionValues;

    /**
     * 可见性角色限制（JSON 数组字符串）。
     * NULL 表示对所有项目成员可见。
     * 格式: [2,7] — 只有角色 ID 为 2 或 7 的成员可以看到此字段。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String visibleToRoles;

    /**
     * 可编辑性角色限制（JSON 数组字符串）。
     * NULL 表示所有可见此字段的用户都能编辑。
     * 格式: [2,7] — 只有角色 ID 为 2 或 7 的成员可以编辑此字段。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String updatableByRoles;

    /**
     * 项目级必填性覆盖。
     * NULL = 继承全局设置，true/false = 项目级覆盖。
     */
    private Boolean isRequired;

    /**
     * 项目级默认值覆盖。
     * NULL = 继承全局设置。
     */
    private String defaultValue;

    /**
     * 值过滤依赖的源字段 ID（指向 custom_field_definition）。
     * 为 NULL 表示无值过滤。
     * 与条件显示（conditionFieldId）是两个独立机制：
     * - 条件显示：控制字段本身是否出现
     * - 值依赖过滤：字段出现，但下拉选项被缩小
     */
    private Long filterFieldId;

    /**
     * 过滤规则 JSON。
     * 当源字段值为指定值时，本字段只展示指定选项。
     * 格式: [{"whenValue":"optionId1","showOnly":["optionId3","optionId4"]}, ...]
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String filterRules;

    /**
     * 是否为排除记录。
     * true 表示全局字段被从此项目排除（YouTrack "Remove field from project" 行为）。
     * 排除时同时清除该项目内所有工单的字段值。
     */
    private Boolean isExcluded;

    /**
     * 是否已创建项目级独立选项副本（Make Independent Copy）。
     * <ul>
     *   <li>false (默认): 使用全局共享选项集</li>
     *   <li>true: 使用项目独立选项集（custom_field_option.project_id = 本项目）</li>
     * </ul>
     */
    private Boolean hasIndependentOptions;

    /**
     * 是否在工单列表标题左侧以数字徽章形式展示该字段值。
     * 仅对整数（integer）类型字段有效。
     */
    private Boolean showAsBadge;

    /**
     * 徽章颜色规则 JSON 数组。
     * 按数值区间配置颜色，格式: [{"max":1,"color":"#ef4444"},{"max":3,"color":"#f97316"},{"color":"#3b82f6"}]
     * 匹配逻辑：从头到尾遍历，第一个满足 value <= max 的规则生效；无 max 的规则为默认兜底。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String badgeColorRules;

    /**
     * 是否允许字段为空（项目级覆盖）。
     * <ul>
     *   <li>true (默认): 字段可以为空</li>
     *   <li>false: 字段不能为空（Empty Value = "Cannot be empty"）</li>
     * </ul>
     * <p>
     * 当 canBeEmpty=false 且 defaultValue=null 时，表示"无默认值但必填"（YouTrack "No value (required)"）。
     * 此模式下系统不自动应用选项表的 isDefault=true，用户必须主动选择。
     */
    private Boolean canBeEmpty;
}

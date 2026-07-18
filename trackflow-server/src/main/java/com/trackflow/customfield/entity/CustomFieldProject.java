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
}

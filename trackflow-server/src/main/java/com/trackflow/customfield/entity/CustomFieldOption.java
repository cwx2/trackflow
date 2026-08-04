package com.trackflow.customfield.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 列表类型自定义字段的可选项
 * <p>
 * 支持两种作用域：
 * <ul>
 *   <li>projectId = NULL: 全局共享选项集（默认）</li>
 *   <li>projectId = 具体值: 项目独立副本（Make Independent Copy 后创建）</li>
 * </ul>
 */
@Data
@TableName("custom_field_option")
public class CustomFieldOption implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customFieldId;

    /**
     * 所属项目 ID。
     * <ul>
     *   <li>NULL: 全局共享选项集，所有使用该字段的项目共享</li>
     *   <li>具体值: 项目独立副本，仅对指定项目生效</li>
     * </ul>
     */
    private Long projectId;

    private String value;

    private Integer position;

    private Boolean isDefault;

    /** 选项颜色值（HEX 格式如 #4CAF50），为 null 时表示无颜色 */
    private String color;

    /** 选项描述，在下拉选择时以 tooltip 形式展示，帮助用户理解选项含义 */
    private String description;

    /** 是否已归档（归档选项不出现在新值选择列表中，但已引用的值仍可正确展示） */
    private Boolean isArchived;

    /** 仅 state 类型字段使用：标记该状态值是否视为"已解决"（对应 YouTrack 的 isResolved 属性） */
    private Boolean isResolved;

    /** 选项负责人用户 ID（仅 ownedField 类型使用），指向 sys_user */
    private Long ownerUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

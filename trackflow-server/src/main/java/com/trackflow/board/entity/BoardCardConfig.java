package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板卡片配置实体。
 * 项目级配置，控制看板卡片上展示的字段和颜色方案。
 */
@Data
@TableName(value = "board_card_config", autoResultMap = true)
public class BoardCardConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    /** 卡片上显示的字段列表（JSON 数组，存储为 String） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String visibleFields;

    /** 颜色方案：none / priority / type / project */
    private String colorScheme;

    /**
     * 当前估算字段 ID（引用 custom_field_definition），卡片上显示可编辑的估算值。
     * 对应 YouTrack Board Settings Cards Tab 的 "Current estimation field"。
     */
    private Long currentEstimationFieldId;

    /**
     * 原始估算字段 ID（引用 custom_field_definition），Sprint 开始时记录的不可变快照值。
     * 对应 YouTrack Board Settings Cards Tab 的 "Original estimation field"。
     */
    private Long originalEstimationFieldId;

    /**
     * 每个字段的显示格式，JSONB 对象，key 为字段名，value 为 "full_name" 或 "initial"。
     * 示例：{"assignee":"initial","priority":"full_name"}
     * 未配置的字段默认使用 full_name 模式。
     * 对应 YouTrack Board Settings Cards Tab 的字段 Display menu（Full name / Initial）。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String fieldDisplayModes;

    /**
     * 是否在卡片自定义字段值旁显示颜色指示器。
     * 对应 YouTrack Board Settings > Cards Tab 的 "Show colors for other custom fields" 开关。
     * 默认 true（与 YouTrack 行为一致）。
     */
    private Boolean showCustomFieldColors;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

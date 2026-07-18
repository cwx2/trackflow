package com.trackflow.board.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板基本设置实体。
 * 项目级配置，控制看板名称和访问权限。
 */
@Data
@TableName(value = "board_general_config", autoResultMap = true)
public class BoardGeneralConfig implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;

    /** 看板显示名称，为空时前端使用默认名 */
    private String name;

    /** 可查看看板的角色代码列表（JSONB 数组） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String canViewRoles;

    /** 可编辑看板设置的角色代码列表（JSONB 数组） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String canEditRoles;

    /** 看板过滤模式：all=显示所有工单, active_sprint=仅显示活跃Sprint工单 */
    private String filterMode;

    /** 已完成工单保留天数（NULL 表示不限制） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer doneRetentionDays;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

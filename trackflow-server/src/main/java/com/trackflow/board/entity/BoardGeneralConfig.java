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

    /** 看板过滤模式：all=显示所有工单, active_sprint=仅显示活跃Sprint工单, query=按查询过滤 */
    private String filterMode;

    /** 查询过滤条件（JSON数组），当 filter_mode='query' 时生效 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String filterQuery;

    /** 已完成工单保留天数（NULL 表示不限制） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer doneRetentionDays;

    /** 看板列标识字段：status=按状态分列(默认), priority=按优先级分列 */
    private String columnField;

    /**
     * 是否允许卡片分配到多个迭代（Sprint）。
     * 对应 YouTrack Board Settings > Cards Tab: Allow cards to be assigned to multiple sprints。
     * 默认 false，向后兼容。
     */
    private Boolean allowMultipleSprints;

    /**
     * Backlog 视图模式：list=平铺列表（默认），tree=树形层级。
     * 对应 YouTrack Board Settings > Backlog Settings: View mode。
     */
    private String backlogViewMode;

    /**
     * 过滤 Backlog 工单的保存搜索 ID。
     * null 表示使用默认过滤（不在看板上的工单）。
     * 对应 YouTrack Board Settings > Backlog Settings: Saved search。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long backlogSavedQueryId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板基本设置 VO — 返回给前端。
 */
@Data
public class BoardGeneralConfigVO {

    /** 看板配置版本号（用于乐观锁并发控制） */
    private Integer configVersion;

    /** 看板名称（为空时前端使用项目名 + "看板"） */
    private String name;

    /** 可查看看板的角色代码列表 */
    private List<String> canViewRoles;

    /** 可编辑看板设置的角色代码列表 */
    private List<String> canEditRoles;

    /** 看板过滤模式：all | active_sprint | query */
    private String filterMode;

    /** 查询过滤条件（JSON 数组字符串），当 filterMode='query' 时有值 */
    private String filterQuery;

    /** 已完成工单保留天数（null 表示不限制） */
    private Integer doneRetentionDays;

    /** 看板列标识字段：status | priority（默认 status） */
    private String columnField;

    /**
     * 是否允许卡片分配到多个迭代（Sprint）。
     * 对应 YouTrack Board Settings > Cards Tab: Allow cards to be assigned to multiple sprints。
     */
    private Boolean allowMultipleSprints;

    /**
     * Backlog 视图模式：list=平铺列表（默认），tree=树形层级。
     * 对应 YouTrack Board Settings > Backlog Settings: View mode。
     */
    private String backlogViewMode;

    /**
     * 过滤 Backlog 工单的保存搜索 ID（String，VO 中 ID 用 String）。
     * null 表示使用默认过滤（不在看板上的工单）。
     * 对应 YouTrack Board Settings > Backlog Settings: Saved search。
     */
    private String backlogSavedQueryId;

    /**
     * 保存搜索名称（backlogSavedQueryId 对应的名称，运行时填充）。
     */
    private String backlogSavedQueryName;

    /** 当前用户是否有看板查看权限（运行时计算） */
    private Boolean currentUserCanView;

    /** 当前用户是否有看板编辑权限（运行时计算） */
    private Boolean currentUserCanEdit;
}

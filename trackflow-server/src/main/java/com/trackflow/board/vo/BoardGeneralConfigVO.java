package com.trackflow.board.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板基本设置 VO — 返回给前端。
 */
@Data
public class BoardGeneralConfigVO {

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

    /** 当前用户是否有看板查看权限（运行时计算） */
    private Boolean currentUserCanView;

    /** 当前用户是否有看板编辑权限（运行时计算） */
    private Boolean currentUserCanEdit;
}

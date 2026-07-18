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

    /** 看板过滤模式：all | active_sprint */
    private String filterMode;

    /** 已完成工单保留天数（null 表示不限制） */
    private Integer doneRetentionDays;
}

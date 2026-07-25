package com.trackflow.project.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 项目详情 VO（含当前用户角色、成员统计等附加信息）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDetailVO extends ProjectVO {

    /** 当前登录用户在该项目中的角色名称（如"观察者"、"开发人员"）——向后兼容，取第一个角色 */
    private String myRoleName;

    /** 当前登录用户在该项目中的角色代码（如 observer、developer）——向后兼容，取第一个角色 */
    private String myRoleCode;

    /** 当前登录用户在该项目中的所有角色名称列表 */
    private List<String> myRoleNames;

    /** 当前登录用户在该项目中的所有角色代码列表 */
    private List<String> myRoleCodes;

    /** 项目成员总数 */
    private Integer memberCount;

    /** 项目负责人显示名称 */
    private String leadName;

    /** 项目负责人状态（active/disabled） */
    private String leadStatus;
}

package com.trackflow.project.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目详情 VO（含当前用户角色、成员统计等附加信息）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectDetailVO extends ProjectVO {

    /** 当前登录用户在该项目中的角色名称（如"观察者"、"开发人员"） */
    private String myRoleName;

    /** 当前登录用户在该项目中的角色代码（如 observer、developer） */
    private String myRoleCode;

    /** 项目成员总数 */
    private Integer memberCount;

    /** 项目负责人显示名称 */
    private String leadName;
}

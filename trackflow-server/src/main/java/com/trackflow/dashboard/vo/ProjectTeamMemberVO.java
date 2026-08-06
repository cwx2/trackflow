package com.trackflow.dashboard.vo;

import lombok.Data;

/**
 * 项目团队成员信息（用于 Dashboard Widget）
 * 包含成员基本信息和未关闭工单数量
 */
@Data
public class ProjectTeamMemberVO {

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 显示名称 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 项目中的角色名称列表（逗号分隔） */
    private String roleName;

    /** 分配给该成员的未关闭工单数 */
    private int openIssueCount;
}

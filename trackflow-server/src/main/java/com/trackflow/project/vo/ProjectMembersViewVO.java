package com.trackflow.project.vo;

import lombok.Data;

import java.util.List;

/**
 * 项目成员完整视图 — 对标 YouTrack "People" 页面设计
 * 分为两个区域：
 * 1. 项目团队（直接成员 + 用户组）
 * 2. 其他有访问权的用户（通过全局角色获得权限）
 */
@Data
public class ProjectMembersViewVO {
    /** 直接添加到项目的个人成员 */
    private List<ProjectMemberVO> directMembers;
    /** 通过用户组获得项目访问权的组 */
    private List<ProjectGroupMemberVO> groupMembers;
}

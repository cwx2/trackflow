package com.trackflow.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 角色已分配用户视图
 */
@Data
public class RoleUsersVO {

    /**
     * 角色ID
     */
    private String roleId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色类型：global / project
     */
    private String roleType;

    /**
     * 全局角色：拥有该角色的用户列表
     */
    private List<UserVO> globalUsers;

    /**
     * 项目角色：按项目分组的用户列表
     */
    private List<ProjectRoleGroup> projectGroups;

    /**
     * 总用户数（全局 + 项目去重后的数量）
     */
    private Integer totalUserCount;

    @Data
    public static class ProjectRoleGroup {
        private String projectId;
        private String projectName;
        private String projectKey;
        private List<UserVO> users;
    }
}

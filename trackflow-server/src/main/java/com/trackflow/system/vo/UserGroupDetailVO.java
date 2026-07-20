package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户组详情 VO（包含成员列表和角色分配）
 */
@Data
public class UserGroupDetailVO {

    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 组成员列表 */
    private List<MemberInfo> members;

    /** 组角色分配列表 */
    private List<RoleAssignment> roles;

    @Data
    public static class MemberInfo {
        private String userId;
        private String username;
        private String displayName;
        private String email;
        private String avatarUrl;
        private LocalDateTime joinedAt;
    }

    @Data
    public static class RoleAssignment {
        private String id;
        private String roleId;
        private String roleName;
        private String roleCode;
        private String roleType;
        /** null 表示全局作用域 */
        private String projectId;
        private String projectName;
        private String projectKey;
        /**
         * 作用域描述：
         * - "global" = 全局角色
         * - "all_projects" = 项目角色，全局作用域（对所有项目生效）
         * - "project" = 项目角色，指定项目
         */
        private String scope;
        private LocalDateTime createdAt;
    }
}

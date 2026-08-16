package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String orgId;
    private String status;
    private String banStatus;
    private String banReason;
    private LocalDateTime bannedAt;
    private String bannedByName;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    /** 用户的全局角色列表（列表页展示用） */
    private List<GlobalRoleInfo> globalRoles;

    /** 用户的项目角色摘要列表（列表页展示用，显示角色名和所属项目） */
    private List<ProjectRoleSummary> projectRoles;

    @Data
    public static class GlobalRoleInfo {
        private String id;
        private String name;
        private String code;

        public GlobalRoleInfo() {}

        public GlobalRoleInfo(String id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }
    }

    /**
     * 项目角色摘要 — 用于列表页展示用户在各项目中的角色
     */
    @Data
    public static class ProjectRoleSummary {
        private String roleName;
        private String roleCode;
        private List<String> projectKeys;

        public ProjectRoleSummary() {}

        public ProjectRoleSummary(String roleName, String roleCode, List<String> projectKeys) {
            this.roleName = roleName;
            this.roleCode = roleCode;
            this.projectKeys = projectKeys;
        }
    }
}

package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户完整档案 VO
 * 包含基本信息、全局角色、项目角色分布、最近活动
 */
@Data
public class UserProfileVO {

    // ===== 基本信息 =====
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    // ===== 全局角色 =====
    private List<RoleInfo> globalRoles;

    // ===== 项目角色分布 =====
    private List<ProjectRoleInfo> projectRoles;

    // ===== 最近活动 =====
    private List<ActivityInfo> recentActivities;

    @Data
    public static class RoleInfo {
        private String id;
        private String name;
        private String code;
    }

    @Data
    public static class ProjectRoleInfo {
        private String projectId;
        private String projectName;
        private String projectKey;
        private String roleName;
        private String roleCode;
        private LocalDateTime joinedAt;
    }

    @Data
    public static class ActivityInfo {
        private String id;
        private String issueId;
        private String issueKey;
        private String issueTitle;
        private String action;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private LocalDateTime createdAt;
    }
}

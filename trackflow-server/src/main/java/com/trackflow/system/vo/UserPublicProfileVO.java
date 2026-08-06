package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户公开资料 VO
 * 根据访问者权限返回不同级别的信息：
 * - 普通成员访问共同项目成员：基础公开信息
 * - 系统管理员访问任意用户：完整信息
 */
@Data
public class UserPublicProfileVO {

    // ===== 基础公开信息（所有有权限访问者可见） =====
    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;

    /**
     * 与访问者共同参与的项目列表（带角色）
     * 普通成员可见
     */
    private List<CommonProjectInfo> commonProjects;

    // ===== 管理员额外信息（仅系统管理员可见） =====
    private String email;
    private LocalDateTime createdAt;
    private String status;
    private String banStatus;
    private List<RoleInfo> globalRoles;
    private List<AllProjectRoleInfo> allProjectRoles;

    /**
     * 是否为管理员视角（前端据此决定展示哪些区域）
     */
    private boolean adminView;

    @Data
    public static class CommonProjectInfo {
        private String projectId;
        private String projectName;
        private String projectKey;
        /** 目标用户在该项目中的角色 */
        private String roleName;
        private String roleCode;
    }

    @Data
    public static class RoleInfo {
        private String id;
        private String name;
        private String code;
    }

    @Data
    public static class AllProjectRoleInfo {
        private String projectId;
        private String projectName;
        private String projectKey;
        private String roleName;
        private String roleCode;
    }
}

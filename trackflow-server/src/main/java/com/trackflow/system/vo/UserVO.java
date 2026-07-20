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
}

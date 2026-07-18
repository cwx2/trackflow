package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

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
}

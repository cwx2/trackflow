package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局项目角色分配 VO
 */
@Data
public class GlobalMemberVO {

    private String id;

    private String userId;
    private String username;
    private String displayName;
    private String email;

    private String roleId;
    private String roleName;
    private String roleCode;

    private LocalDateTime createdAt;
    private String createdByName;
}

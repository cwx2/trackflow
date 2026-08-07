package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织级访问授权 VO
 */
@Data
public class OrgAccessVO {
    private String id;
    private String orgId;
    private String userId;
    private String userName;
    private String userDisplayName;
    private String roleId;
    private String roleName;
    private LocalDateTime createdAt;
}

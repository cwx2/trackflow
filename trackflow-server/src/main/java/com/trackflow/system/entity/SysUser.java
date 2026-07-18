package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String keycloakId;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String phone;
    private Long orgId;
    private String status;
    private String banStatus;
    private String banReason;
    private LocalDateTime bannedAt;
    private Long bannedBy;
    private LocalDateTime lastLoginAt;
}

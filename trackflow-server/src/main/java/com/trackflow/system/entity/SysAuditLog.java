package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统审计日志实体
 */
@Data
@TableName(value = "sys_audit_log", autoResultMap = true)
public class SysAuditLog implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作者用户 ID */
    private Long operatorId;

    /** 操作类型：assign_global_role, remove_global_role, update_role_permissions, disable_user, enable_user */
    private String action;

    /** 目标类型：user, role */
    private String targetType;

    /** 目标 ID */
    private Long targetId;

    /** 变更详情（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String details;

    /** 操作者 IP 地址 */
    private String ipAddress;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志 VO
 */
@Data
public class AuditLogVO {

    private String id;

    /** 操作者 ID */
    private String operatorId;

    /** 操作者显示名 */
    private String operatorName;

    /** 操作类型 */
    private String action;

    /** 目标类型：user, role */
    private String targetType;

    /** 目标 ID */
    private String targetId;

    /** 目标名称（用户名/角色名） */
    private String targetName;

    /** 变更详情 JSON */
    private String details;

    /** 操作者 IP */
    private String ipAddress;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

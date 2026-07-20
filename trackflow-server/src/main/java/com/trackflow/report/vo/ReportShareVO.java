package com.trackflow.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表共享条目 VO
 */
@Data
public class ReportShareVO {

    private String id;

    /** 共享目标类型：user / group */
    private String targetType;

    /** 共享目标 ID */
    private String targetId;

    /** 目标名称（用户显示名 或 用户组名称） */
    private String targetName;

    /** 权限级别：view / edit */
    private String permission;

    private LocalDateTime createdAt;
}

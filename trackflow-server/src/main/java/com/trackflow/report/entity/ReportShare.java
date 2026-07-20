package com.trackflow.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报表共享关联实体
 */
@Data
@TableName("report_share")
public class ReportShare implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reportId;

    /** 共享目标类型：user / group */
    private String targetType;

    /** 共享目标 ID（用户 ID 或用户组 ID） */
    private Long targetId;

    /** 权限级别：view / edit */
    private String permission;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

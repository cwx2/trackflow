package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单受限可见用户关联表（visibility=restricted 时生效）
 */
@Data
@TableName("issue_visibility_user")
public class IssueVisibilityUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long issueId;
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

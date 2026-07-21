package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单手动排序 - 存储用户对工单列表的自定义拖拽排序
 */
@Data
@TableName("issue_manual_order")
public class IssueManualOrder implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 排序上下文类型：project / query */
    private String contextType;

    /** 上下文 ID：project_id 或 saved_query_id */
    private Long contextId;

    /** 用户 ID，null 表示所有者设置的全局排序 */
    private Long userId;

    /** 工单 ID */
    private Long issueId;

    /** 排序位置，从 0 开始 */
    private Integer position;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "issue")
public class Issue implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String issueKey;
    private String title;
    private String description;
    private String issueType;
    private Long issueTypeOptionId;
    private Long statusId;
    private String priority;
    private Long priorityOptionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeId;
    private Long reporterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sprintId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate dueDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal estimatedHours;
    private BigDecimal spentHours;
    /** 派生字段：自身 + 所有后代 spent_hours 总和 */
    private BigDecimal derivedSpentHours;
    /** 派生字段：自身 + 所有后代 estimated_hours 总和 */
    private BigDecimal derivedEstimatedHours;
    /** 直接子工单总数（不含软删除） */
    private Integer childCount;
    /** 已关闭的直接子工单数 */
    private Integer childClosedCount;
    /** 投票数冗余字段 */
    private Integer voteCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolvedAt;
    /**
     * 工单可见性：public（项目所有成员可见）或 restricted（仅限指定用户）。
     * 默认 public，修改后需同步维护 issue_visibility_user 表。
     */
    private String visibility;
    private LocalDateTime deletedAt;

    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

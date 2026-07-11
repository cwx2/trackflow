package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "issue", autoResultMap = true)
public class Issue implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String issueKey;
    private String title;
    private String description;
    private String issueType;
    private Long statusId;
    private String priority;
    private Long assigneeId;
    private Long reporterId;
    private Long sprintId;
    private Long parentId;
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private BigDecimal spentHours;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String customFields;
    private LocalDateTime resolvedAt;
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package com.trackflow.sprint.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sprint")
public class Sprint implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String name;
    private String goal;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;

    /** Sprint 实际激活时间（区别于计划开始日期 start_date） */
    private LocalDateTime startedAt;
    /** 激活时的总预估工时快照（所有工单 estimated_hours 之和） */
    private BigDecimal startScopeHours;
    /** 激活时的工单数量快照 */
    private Integer startScopeIssues;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

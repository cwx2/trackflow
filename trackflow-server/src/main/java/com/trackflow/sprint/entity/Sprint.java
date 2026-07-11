package com.trackflow.sprint.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
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
    private String status; // planned / active / completed
    private LocalDate startDate;
    private LocalDate endDate;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

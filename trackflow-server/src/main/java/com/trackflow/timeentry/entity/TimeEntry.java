package com.trackflow.timeentry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("time_entry")
public class TimeEntry {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long issueId;
    private Long userId;
    private LocalDate workDate;
    private Integer duration;       // minutes
    private Integer startTime;      // minutes from midnight (e.g. 540 = 09:00)
    private String workType;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

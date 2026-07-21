package com.trackflow.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报表收藏实体
 */
@Data
@TableName("report_favorite")
public class ReportFavorite implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long reportId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

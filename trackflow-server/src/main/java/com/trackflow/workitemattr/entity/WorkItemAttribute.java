package com.trackflow.workitemattr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作项属性定义（系统级）
 */
@Data
@TableName("work_item_attribute")
public class WorkItemAttribute {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private Boolean isBuiltin;
    private Integer position;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}

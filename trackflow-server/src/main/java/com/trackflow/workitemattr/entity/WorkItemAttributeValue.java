package com.trackflow.workitemattr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作项属性值（每个属性可有多个可选值）
 */
@Data
@TableName("work_item_attribute_value")
public class WorkItemAttributeValue {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long attributeId;
    private String name;
    private String color;
    private Integer position;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

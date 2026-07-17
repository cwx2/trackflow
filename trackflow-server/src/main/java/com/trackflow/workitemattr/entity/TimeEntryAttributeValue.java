package com.trackflow.workitemattr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工时记录-属性值关联
 */
@Data
@TableName("time_entry_attribute_value")
public class TimeEntryAttributeValue {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long timeEntryId;
    private Long attributeId;
    private Long valueId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

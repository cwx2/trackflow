package com.trackflow.workitemattr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作项属性-项目关联
 */
@Data
@TableName("work_item_attribute_project")
public class WorkItemAttributeProject {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long attributeId;
    private Long projectId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

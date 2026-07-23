package com.trackflow.workitemattr.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作项属性值-项目关联
 * 控制每个项目可见的属性值子集（对标 YouTrack 项目级独立管理属性值）
 */
@Data
@TableName("work_item_attribute_value_project")
public class WorkItemAttributeValueProject {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long valueId;
    private Long projectId;
    private Integer position;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

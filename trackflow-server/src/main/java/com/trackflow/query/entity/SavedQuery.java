package com.trackflow.query.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "saved_query", autoResultMap = true)
public class SavedQuery implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long projectId;
    private Long userId;
    private Boolean shared;
    private Boolean pinned;
    private String folder;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String filters;       // JSON array

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String columns;       // JSON array

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String sortCriteria;  // JSON array

    private String groupBy;
    private String icon;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

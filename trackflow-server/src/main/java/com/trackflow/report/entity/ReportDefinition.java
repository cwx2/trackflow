package com.trackflow.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "report_definition", autoResultMap = true)
public class ReportDefinition implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private Long projectId;
    private String type;       // issue_count / burndown / by_assignee / by_status / custom

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;     // JSON: filters + groupBy + chartType

    private Boolean shared;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

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
    private String type;       // @see ReportType 枚举

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;     // JSON: groupBy + filters + chartType，@see ReportGroupBy

    private Boolean shared;

    /** 系统预置报表标记（不可被普通用户删除） */
    private Boolean isSystem;

    /** 上次计算完成时间 */
    private LocalDateTime lastCalculatedAt;

    /** 缓存的计算结果（JSON 格式，对应 ReportExecuteResultVO） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String cachedResult;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

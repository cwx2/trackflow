package com.trackflow.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仪表盘微件实体
 */
@Data
@TableName(value = "dashboard_widget", autoResultMap = true)
public class DashboardWidget implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long dashboardId;

    /** 微件类型：note, report, report_distribution, issue_list, activity_feed, number_card, etc. */
    private String widgetType;

    private String title;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String config;

    /** 关联报表定义（报表类微件） */
    private Long reportId;

    /** 网格列位置（0-based） */
    private Integer positionX;

    /** 网格行位置（0-based） */
    private Integer positionY;

    /** 占几列（1-12） */
    private Integer width;

    /** 占几行 */
    private Integer height;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

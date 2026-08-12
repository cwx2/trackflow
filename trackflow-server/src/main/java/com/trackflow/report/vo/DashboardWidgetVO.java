package com.trackflow.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微件 VO
 */
@Data
public class DashboardWidgetVO {

    private String id;
    private String dashboardId;
    private String widgetType;
    private String title;
    private String config;
    private String reportId;
    private Integer positionX;
    private Integer positionY;
    private Integer width;
    private Integer height;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    /** 从 config.projectId 解析出的项目名称（用于标题区分） */
    private String projectName;
}

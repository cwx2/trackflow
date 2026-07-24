package com.trackflow.report.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘详情 VO（含所有 Widget）
 */
@Data
public class DashboardDetailVO {

    private String id;
    private String name;
    private String description;
    private String ownerId;
    private String ownerName;
    private Boolean shared;
    private Integer layoutVersion;
    /** 是否为系统默认仪表盘 */
    private Boolean isSystemDefault;
    /** 精细化共享对象数量 */
    private Integer shareCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 仪表盘包含的所有微件 */
    private List<DashboardWidgetVO> widgets;
}

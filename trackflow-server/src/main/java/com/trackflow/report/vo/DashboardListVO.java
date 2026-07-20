package com.trackflow.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仪表盘列表 VO（不含 Widget 明细）
 */
@Data
public class DashboardListVO {

    private String id;
    private String name;
    private String description;
    private String ownerId;
    private String ownerName;
    private Boolean shared;
    private Integer widgetCount;
    /** 精细化共享对象数量（用户+组的总数） */
    private Integer shareCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
    /** 当前用户是否收藏了该仪表盘 */
    private Boolean favorited;
    /** 是否为当前用户的默认仪表盘 */
    private Boolean isDefault;
    /** 是否为系统默认仪表盘 */
    private Boolean isSystemDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

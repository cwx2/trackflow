package com.trackflow.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织详情 VO（包含项目数量等聚合信息）
 */
@Data
public class OrgDetailVO {
    private String id;
    private String name;
    private String code;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer projectCount;
}

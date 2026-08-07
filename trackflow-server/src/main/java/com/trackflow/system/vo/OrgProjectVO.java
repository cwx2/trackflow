package com.trackflow.system.vo;

import lombok.Data;

/**
 * 组织下项目的简洁 VO（用于组织详情页项目标签页）
 */
@Data
public class OrgProjectVO {
    private String id;
    private String name;
    private String key;
    private String status;
    private Integer issueCount;
}

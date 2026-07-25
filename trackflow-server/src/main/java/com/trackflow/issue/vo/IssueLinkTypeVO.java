package com.trackflow.issue.vo;

import lombok.Data;

/**
 * 工单关联类型 VO - 返回给前端
 */
@Data
public class IssueLinkTypeVO {
    private String id;
    private String name;
    private String outwardName;
    private String inwardName;
    private String direction;
    private Boolean isSystem;
}

package com.trackflow.system.vo;

import lombok.Data;

@Data
public class RoleVO {
    private String id;
    private String name;
    private String code;
    private String description;
    private String roleType;
    private Boolean builtin;
    private Integer sortOrder;

    /**
     * 被分配该角色的用户总数
     */
    private Integer userCount;
}

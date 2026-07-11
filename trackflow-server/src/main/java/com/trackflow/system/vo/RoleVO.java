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
}

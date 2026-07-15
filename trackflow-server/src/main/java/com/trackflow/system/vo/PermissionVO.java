package com.trackflow.system.vo;

import lombok.Data;

/**
 * 权限定义 VO
 */
@Data
public class PermissionVO {

    private String code;
    private String name;
    private String description;
    private String scope;
}

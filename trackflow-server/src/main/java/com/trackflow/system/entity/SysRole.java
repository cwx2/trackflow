package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统角色实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String name;
    private String code;
    private String description;
    private String roleType;  // global / project
    private Boolean builtin;
    private Integer sortOrder;
}

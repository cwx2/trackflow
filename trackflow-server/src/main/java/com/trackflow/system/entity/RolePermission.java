package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色权限关联实体
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private String permission;
}

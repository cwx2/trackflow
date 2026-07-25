package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限隐含关系实体 - 定义权限之间的依赖/蕴含关系
 * <p>
 * permission_code 隐含 implied_code，即拥有上层权限时自动拥有底层权限
 */
@Data
@TableName("sys_permission_implication")
public class PermissionImplication implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 上层权限编码（拥有此权限时...）
     */
    private String permissionCode;

    /**
     * 被隐含的底层权限编码（...自动拥有此权限）
     */
    private String impliedCode;
}

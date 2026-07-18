package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组角色分配实体
 * <p>
 * project_id = null 表示全局角色分配
 * project_id != null 表示项目级角色分配
 */
@Data
@TableName("user_group_role")
public class UserGroupRole implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private Long roleId;
    private Long projectId;
    private LocalDateTime createdAt;
}

package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联实体
 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createdAt;
}

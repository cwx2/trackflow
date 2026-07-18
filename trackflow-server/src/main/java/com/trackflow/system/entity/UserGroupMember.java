package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户组成员关联实体
 */
@Data
@TableName("user_group_member")
public class UserGroupMember implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private Long userId;
    private LocalDateTime createdAt;
}

package com.trackflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局项目角色分配实体。
 * 表示用户在所有项目（含未来新建的项目）中拥有指定的项目角色。
 */
@Data
@TableName("global_member")
public class GlobalMember implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createdAt;

    private Long createdBy;
}

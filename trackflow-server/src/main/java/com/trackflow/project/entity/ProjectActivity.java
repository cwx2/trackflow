package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目活动日志实体
 */
@Data
@TableName(value = "project_activity", autoResultMap = true)
public class ProjectActivity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 操作者 ID */
    private Long userId;

    /** 操作类型：add_member, remove_member, change_role, archive, restore */
    private String action;

    /** 被操作的用户 ID */
    private Long targetUserId;

    /** 扩展信息（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String detail;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

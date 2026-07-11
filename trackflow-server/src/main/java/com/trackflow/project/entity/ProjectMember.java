package com.trackflow.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目成员实体
 */
@Data
@TableName("project_member")
public class ProjectMember implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long userId;
    private Long roleId;
    private LocalDateTime joinedAt;
}

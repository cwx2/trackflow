package com.trackflow.issue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单关联类型定义 - 对应 issue_link_type 表
 * 参考 YouTrack Link Types 管理功能，支持 CRUD 管理
 */
@Data
@TableName("issue_link_type")
public class IssueLinkType implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 内部标识名称（如 blocks, duplicates, relates_to, parent_of） */
    private String name;

    /** 目标工单显示名（如 blocks, duplicates, relates to, parent of） */
    private String outwardName;

    /** 源工单显示名（如 is blocked by, is duplicated by, relates to, subtask of） */
    private String inwardName;

    /** 方向类型：DIRECTED（有向）、UNDIRECTED（无向/对称）、AGGREGATION（聚合） */
    private String direction;

    /** 是否为系统内置类型（不可删除、不可修改方向） */
    private Boolean isSystem;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

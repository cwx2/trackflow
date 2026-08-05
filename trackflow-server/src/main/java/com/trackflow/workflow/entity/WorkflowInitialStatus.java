package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流初始状态配置 — 定义每个(项目, 工单类型)组合创建工单时的默认初始状态
 *
 * @author TrackFlow
 * @since 1.0
 */
@Data
@TableName("workflow_initial_status")
public class WorkflowInitialStatus implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目 ID，null 表示全局默认 */
    private Long projectId;

    /** 工单类型，* 表示该项目所有类型的默认值 */
    private String issueType;

    /** 初始状态 ID */
    private Long statusId;

    private LocalDateTime createdAt;

    private Long createdBy;
}

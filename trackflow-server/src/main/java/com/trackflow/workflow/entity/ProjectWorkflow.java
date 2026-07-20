package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目与工作流定义的绑定关系。
 * <p>
 * 一个项目可以绑定多个工作流定义，一个工作流定义可以被多个项目共享。
 * 对标 YouTrack 的 Workflow attach/detach 机制。
 */
@Data
@TableName("project_workflow")
public class ProjectWorkflow implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目 ID */
    private Long projectId;

    /** 工作流定义 ID */
    private Long workflowDefinitionId;

    /** 绑定时间 */
    private LocalDateTime createdAt;
}

package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流变更审计日志
 */
@Data
@TableName("workflow_activity")
public class WorkflowActivity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目ID，null 表示全局工作流 */
    private Long projectId;

    /** 工单类型 */
    private String issueType;

    /** 角色ID */
    private Long roleId;

    /** 操作用户ID */
    private Long userId;

    /** 操作类型：workflow_updated */
    private String action;

    /** 旧值描述 */
    private String oldValue;

    /** 新值描述 */
    private String newValue;

    /** 人类可读的变更摘要 */
    private String summary;

    /** JSON 格式的变更明细（added/removed 转换列表） */
    private String details;

    private LocalDateTime createdAt;
}

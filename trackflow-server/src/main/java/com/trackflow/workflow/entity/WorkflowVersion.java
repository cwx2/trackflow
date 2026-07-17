package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流转换矩阵版本追踪（乐观锁并发控制）
 * <p>
 * 每个 (projectId, issueType, roleId, author, assignee) 组合维护一个版本号，
 * 更新时通过 CAS 检测并发冲突。
 */
@Data
@TableName("workflow_version")
public class WorkflowVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID，null 表示全局工作流 */
    private Long projectId;

    /** 工单类型 */
    private String issueType;

    /** 角色ID */
    private Long roleId;

    /** 是否 Author 模式 */
    private Boolean author;

    /** 是否 Assignee 模式 */
    private Boolean assignee;

    /** 版本号，每次更新递增 */
    private Integer version;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 最后更新人 */
    private Long updatedBy;
}

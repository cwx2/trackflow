package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName(value = "workflow_transition", autoResultMap = true)
public class WorkflowTransition implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String issueType;
    private Long roleId;
    private Long oldStatusId;
    private Long newStatusId;

    /**
     * 此规则是否仅适用于工单创建者（author）。
     * false = 对所有拥有该角色的用户生效（基础规则）
     * true = 仅当用户是工单创建者时额外生效
     */
    private Boolean author;

    /**
     * 此规则是否仅适用于工单负责人（assignee）。
     * false = 对所有拥有该角色的用户生效（基础规则）
     * true = 仅当用户是工单负责人时额外生效
     */
    private Boolean assignee;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String conditions;

    /** 所属工作流定义 ID（迁移过渡期可空） */
    private Long workflowDefinitionId;

    /** 是否要求此转换必须附带评论/理由 */
    private Boolean requireComment;

    /** 转换显示名（如"开始处理"），为空时前端使用目标状态名 */
    private String transitionName;

    /** 是否标记为初始状态（新建该类型工单时默认进入的状态） */
    private Boolean isInitial;
}

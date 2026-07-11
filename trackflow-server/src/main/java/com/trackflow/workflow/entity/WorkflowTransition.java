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

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String conditions;
}

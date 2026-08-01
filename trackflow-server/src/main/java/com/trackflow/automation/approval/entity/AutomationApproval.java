package com.trackflow.automation.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "automation_approval", autoResultMap = true)
public class AutomationApproval {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long executionId;
    private Long nodeExecutionId;
    private String nodeId;
    private String title;
    private String description;
    private String riskLevel;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestPayload;
    private String status;
    private Long requestedBy;
    private Long decidedBy;
    private String decisionComment;
    private LocalDateTime expiresAt;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}

package com.trackflow.automation.workitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "automation_work_item", autoResultMap = true)
public class AutomationWorkItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long automationId;
    private Long issueId;
    private String correlationId;
    private String state;
    private Integer priority;
    private String leaseOwner;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private Integer attempt;
    private Integer maxAttempts;
    private LocalDateTime availableAt;
    private Long executionId;
    private String lastError;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

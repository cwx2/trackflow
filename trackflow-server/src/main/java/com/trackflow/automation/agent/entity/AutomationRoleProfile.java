package com.trackflow.automation.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "automation_role_profile", autoResultMap = true)
public class AutomationRoleProfile {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    private String providerType;
    private String model;
    private String systemPrompt;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String toolPolicy;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String outputSchema;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String workspacePolicy;
    private Boolean enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

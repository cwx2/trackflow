package com.trackflow.rule.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则定义 VO
 */
@Data
public class RuleDefinitionVO {

    private String id;
    private String name;
    private String description;
    private String projectId;
    private String projectName;
    private Boolean enabled;
    private String triggerType;
    private String triggerConfig;
    private String scoreFormula;
    private String scoreConfig;
    private String targetField;
    private String scheduleCron;
    private String dedupStrategy;
    private String actions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdByName;
    private LocalDateTime lastExecutedAt;
    private Long executionCount;
}

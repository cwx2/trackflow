package com.trackflow.workflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则执行日志 VO（返回前端）。
 */
@Data
public class WorkflowRuleExecutionLogVO {

    private String id;
    private String ruleId;
    private LocalDateTime executedAt;
    private Integer matchedCount;
    private Integer successCount;
    private Integer failureCount;
    private String errorMessage;
    private Integer durationMs;
}

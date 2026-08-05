package com.trackflow.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * On-schedule 规则执行日志实体。
 */
@Data
@TableName("workflow_rule_execution_log")
public class WorkflowRuleExecutionLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则 ID */
    private Long ruleId;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 匹配到的工单数 */
    private Integer matchedCount;

    /** 成功执行数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failureCount;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Integer durationMs;

    /** 触发规则的工单 Key（on-change 规则为单个工单，on-schedule 规则为 NULL） */
    private String issueKey;
}

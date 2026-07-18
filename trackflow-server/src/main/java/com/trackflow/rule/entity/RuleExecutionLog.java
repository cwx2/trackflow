package com.trackflow.rule.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.trackflow.common.handler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 规则执行记录实体
 */
@Data
@TableName(value = "rule_execution_log", autoResultMap = true)
public class RuleExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private Long issueId;

    private Long targetUserId;

    private BigDecimal score;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String scoreDetail;

    private LocalDateTime executedAt;

    private LocalDate executionDate;

    private String note;
}

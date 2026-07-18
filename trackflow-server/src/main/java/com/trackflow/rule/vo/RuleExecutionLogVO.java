package com.trackflow.rule.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 规则执行记录 VO
 */
@Data
public class RuleExecutionLogVO {

    private String id;
    private String ruleId;
    private String ruleName;
    private String issueId;
    private String issueKey;
    private String issueTitle;
    private String targetUserId;
    private String targetUserName;
    private BigDecimal score;
    private String scoreDetail;
    private LocalDateTime executedAt;
    private LocalDate executionDate;
    private String note;
}

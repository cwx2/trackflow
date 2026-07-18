package com.trackflow.rule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手动录入执行记录 DTO
 */
@Data
public class CreateExecutionLogDTO {

    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    @NotNull(message = "工单ID不能为空")
    private Long issueId;

    @NotNull(message = "作用对象不能为空")
    private Long targetUserId;

    @NotNull(message = "分数/金额不能为空")
    private BigDecimal score;

    private String scoreDetail;

    private String note;
}

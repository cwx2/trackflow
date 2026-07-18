package com.trackflow.rule.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Set;

/**
 * 执行记录查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RuleExecutionLogQuery extends PageQuery {

    private Long ruleId;
    private Long issueId;
    private Long targetUserId;
    private LocalDate startDate;
    private LocalDate endDate;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "executed_at", "score", "execution_date");
    }
}

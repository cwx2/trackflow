package com.trackflow.workflow.dto;

import com.trackflow.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Set;

/**
 * 工作流变更历史查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowActivityQuery extends PageQuery {

    /** 项目ID，0 表示全局 */
    private Long projectId;

    /** 操作人用户ID */
    private Long userId;

    /** 时间范围开始 */
    private LocalDate startDate;

    /** 时间范围结束 */
    private LocalDate endDate;

    @Override
    protected Set<String> allowedSortFields() {
        return Set.of("id", "created_at");
    }
}

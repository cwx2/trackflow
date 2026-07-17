package com.trackflow.report.mapper.result;

import lombok.Data;

/**
 * 工作负载查询结果行
 * SQL 列：assignee_id, assignee_name, total, done_count
 */
@Data
public class WorkloadRow {
    private Long assigneeId;
    private String assigneeName;
    private Long total;
    private Long doneCount;
}

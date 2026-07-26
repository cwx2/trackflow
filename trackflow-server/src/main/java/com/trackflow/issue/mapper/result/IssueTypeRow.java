package com.trackflow.issue.mapper.result;

import lombok.Data;

/**
 * 工单类型投影查询结果行。
 * <p>
 * 对应 SQL: selectTypeByIds — 批量获取工单的 id、issue_type、child_count。
 * 用于燃尽图过滤泳道标识工单（Epic 且有子工单的工单）。
 */
@Data
public class IssueTypeRow {
    private Long id;
    private String issueType;
    private int childCount;
}

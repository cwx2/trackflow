package com.trackflow.project.mapper.result;

import lombok.Data;

/**
 * 项目成员数量统计结果行。
 * <p>
 * 对应 SQL: countDistinctUsersByProjects — 按项目 ID 分组统计去重成员数。
 */
@Data
public class MemberCountRow {
    private Long projectId;
    private Integer memberCount;
}

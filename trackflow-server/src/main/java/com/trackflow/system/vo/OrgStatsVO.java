package com.trackflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织管理统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgStatsVO {
    /** 组织总数 */
    private long total;
    /** 关联项目总数 */
    private long totalProjects;
    /** 有项目的组织数 */
    private long orgsWithProjects;
}

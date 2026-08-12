package com.trackflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户组统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupStatsVO {
    /** 用户组总数 */
    private long total;
    /** 总成员数（所有组成员之和） */
    private long totalMembers;
    /** 已分配角色的组数 */
    private long groupsWithRoles;
}

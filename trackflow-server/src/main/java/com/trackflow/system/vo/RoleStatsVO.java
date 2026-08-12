package com.trackflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色管理统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleStatsVO {
    /** 总角色数 */
    private long total;
    /** 全局角色数 */
    private long globalRoles;
    /** 项目级角色数 */
    private long projectRoles;
    /** 分配了用户的角色数 */
    private long rolesInUse;
}

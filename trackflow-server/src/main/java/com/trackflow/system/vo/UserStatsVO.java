package com.trackflow.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户管理统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsVO {
    /** 总用户数 */
    private long total;
    /** 启用中 */
    private long active;
    /** 禁用中 */
    private long disabled;
    /** 今日新增 */
    private long todayNew;
}

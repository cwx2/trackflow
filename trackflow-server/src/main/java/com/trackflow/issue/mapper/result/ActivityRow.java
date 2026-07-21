package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动查询结果行（类型安全替代 Map&lt;String, Object&gt;）
 * <p>
 * 用于以下查询：
 * <ul>
 *   <li>selectActivitiesWithUser — Issue 详情页活动列表</li>
 *   <li>selectDashboardActivities — Dashboard 活动流（按 Issue ID）</li>
 *   <li>selectDashboardActivitiesByProjects — Dashboard 活动流（按项目）</li>
 * </ul>
 * <p>
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 * 不同查询返回列不同（如 Dashboard 多了 issue_key/issue_title，
 * 详情页多了 detail），未返回的列保持 null。
 */
@Data
public class ActivityRow {
    private Long id;
    private Long issueId;
    private Long userId;
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String detail;
    private LocalDateTime createdAt;

    // JOIN sys_user
    private String userName;
    private String userAvatar;

    // JOIN issue（仅 Dashboard 查询返回）
    private String issueKey;
    private String issueTitle;
}

package com.trackflow.issue.mapper.result;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 JOIN 用户查询的强类型结果行（替代 Map<String, Object>）。
 * <p>
 * 对应 SQL: selectCommentsWithUser — 评论列表一次 JOIN 查出用户名称。
 * SQL 列名使用下划线命名，MyBatis mapUnderscoreToCamelCase 自动映射。
 */
@Data
public class CommentRow {
    private Long id;
    private Long issueId;
    private Long userId;
    private String content;
    private String source;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 可见性限制的组 ID 列表。NULL=全体可见。
     */
    private List<Long> visibleToGroupIds;

    // JOIN sys_user
    private String userName;
    private String userAvatar;
}

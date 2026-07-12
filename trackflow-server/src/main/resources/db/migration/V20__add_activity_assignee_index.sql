-- ============================================================
-- 为 assignee 类型的活动记录添加条件索引
-- 加速 LEFT JOIN 解析 assignee 字段的用户名查询
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_activity_assignee_field
    ON issue_activity(issue_id)
    WHERE field_name = 'assignee';

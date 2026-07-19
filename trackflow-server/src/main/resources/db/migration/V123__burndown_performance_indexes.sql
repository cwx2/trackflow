-- V123: 燃尽图性能优化索引
-- REQ-471 + REQ-473: Sprint 活动记录按 ID 查询的部分索引

-- 部分索引：加速 sprint 活动记录按 new_value 查询（移入 Sprint）
CREATE INDEX IF NOT EXISTS idx_activity_sprint_new_value
    ON issue_activity (new_value)
    WHERE field_name = 'sprint' AND new_value IS NOT NULL;

-- 部分索引：加速 sprint 活动记录按 old_value 查询（移出 Sprint）
CREATE INDEX IF NOT EXISTS idx_activity_sprint_old_value
    ON issue_activity (old_value)
    WHERE field_name = 'sprint' AND old_value IS NOT NULL;

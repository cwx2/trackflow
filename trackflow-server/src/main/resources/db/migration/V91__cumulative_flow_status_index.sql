-- REQ-311: 为累积流图查询添加复合部分索引
-- 累积流图 SQL 对每个 issue×day 组合执行子查询:
--   SELECT new_value FROM issue_activity WHERE issue_id=? AND field_name='status' AND created_at < ? ORDER BY created_at DESC LIMIT 1
-- 此索引通过 partial index (WHERE field_name='status') + 复合键 (issue_id, created_at DESC) 精确匹配查询模式

CREATE INDEX IF NOT EXISTS idx_activity_status_lookup
ON issue_activity (issue_id, created_at DESC)
WHERE field_name = 'status';

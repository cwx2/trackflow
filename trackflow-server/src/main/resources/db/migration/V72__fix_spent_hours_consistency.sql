-- V72: 修复 issue.spent_hours 与 time_entry 实际数据的不一致
-- 问题：issue.spent_hours 作为冗余缓存，可能因异常/并发导致与 SUM(time_entry.duration)/60 不一致
-- 实证：issue id=2007 spent_hours=1.00 但无任何 time_entry 记录

-- 全量校准：将所有 issue 的 spent_hours 设为 time_entry 表的实际聚合值
UPDATE issue i
SET spent_hours = COALESCE(
    (SELECT ROUND(SUM(te.duration) / 60.0, 2)
     FROM time_entry te
     WHERE te.issue_id = i.id),
    0
)
WHERE i.deleted_at IS NULL;

-- V37: 一次性修复所有 issue 的 spent_hours 字段，使其与 time_entry 明细表保持一致
-- issue.spent_hours = SUM(time_entry.duration) / 60.0，保留 2 位小数
-- 没有工时记录的 issue 设为 0

UPDATE issue i
SET spent_hours = COALESCE(
    (SELECT ROUND(SUM(te.duration) / 60.0, 2)
     FROM time_entry te
     WHERE te.issue_id = i.id),
    0
)
WHERE i.deleted_at IS NULL;

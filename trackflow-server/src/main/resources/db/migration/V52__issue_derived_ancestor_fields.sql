-- V52: 父工单派生属性字段 —— 支持子工单变更时自动聚合更新
-- REQ-113: 子工单变更时，父工单的聚合数据（工时、进度）自动更新

-- 新增派生字段
ALTER TABLE issue ADD COLUMN derived_spent_hours DECIMAL(10,2);
ALTER TABLE issue ADD COLUMN derived_estimated_hours DECIMAL(10,2);
ALTER TABLE issue ADD COLUMN child_count INT NOT NULL DEFAULT 0;
ALTER TABLE issue ADD COLUMN child_closed_count INT NOT NULL DEFAULT 0;

-- 回填现有数据：计算每个父工单的 child_count 和 child_closed_count
UPDATE issue p
SET child_count = sub.cnt,
    child_closed_count = sub.closed_cnt
FROM (
    SELECT i.parent_id,
           COUNT(*) AS cnt,
           COUNT(*) FILTER (WHERE s.is_closed = true) AS closed_cnt
    FROM issue i
    JOIN issue_status s ON s.id = i.status_id
    WHERE i.parent_id IS NOT NULL
      AND i.deleted_at IS NULL
    GROUP BY i.parent_id
) sub
WHERE p.id = sub.parent_id;

-- 回填 derived_spent_hours：直接子节点的 spent_hours 之和 + 自身
UPDATE issue p
SET derived_spent_hours = COALESCE(p.spent_hours, 0) + COALESCE(sub.total_spent, 0)
FROM (
    SELECT parent_id, SUM(COALESCE(spent_hours, 0)) AS total_spent
    FROM issue
    WHERE parent_id IS NOT NULL AND deleted_at IS NULL
    GROUP BY parent_id
) sub
WHERE p.id = sub.parent_id;

-- 回填 derived_estimated_hours：直接子节点的 estimated_hours 之和 + 自身
UPDATE issue p
SET derived_estimated_hours = COALESCE(p.estimated_hours, 0) + COALESCE(sub.total_est, 0)
FROM (
    SELECT parent_id, SUM(COALESCE(estimated_hours, 0)) AS total_est
    FROM issue
    WHERE parent_id IS NOT NULL AND deleted_at IS NULL
    GROUP BY parent_id
) sub
WHERE p.id = sub.parent_id;

-- 添加注释
COMMENT ON COLUMN issue.derived_spent_hours IS '派生字段：自身 + 所有后代子工单的 spent_hours 总和';
COMMENT ON COLUMN issue.derived_estimated_hours IS '派生字段：自身 + 所有后代子工单的 estimated_hours 总和';
COMMENT ON COLUMN issue.child_count IS '直接子工单总数（不含软删除）';
COMMENT ON COLUMN issue.child_closed_count IS '已关闭的直接子工单数';

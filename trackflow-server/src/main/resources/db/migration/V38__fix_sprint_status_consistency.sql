-- ============================================================
-- V38: 修复 Sprint 状态与日期不一致的数据
-- REQ-48: Sprint 状态应与日期逻辑保持一致
-- ============================================================

-- 规则：
-- 1. status='active' 且 end_date < CURRENT_DATE → 改为 completed（已过期的 active Sprint）
-- 2. status='active' 且 start_date > CURRENT_DATE → 改为 planned（还没开始的不应是 active）
-- 3. status='completed' 且 start_date = CURRENT_DATE → 改为 active（今天刚开始不应已完成）

-- 修复 FE1 Sprint 3：end_date=2026-07-14 已过，status 还是 active → completed
UPDATE sprint
SET status = 'completed', updated_at = NOW()
WHERE status = 'active'
  AND end_date IS NOT NULL
  AND end_date < CURRENT_DATE;

-- 修复 Sprint 25：start_date=2026-07-29 > today，status 是 active → planned
UPDATE sprint
SET status = 'planned', updated_at = NOW()
WHERE status = 'active'
  AND start_date IS NOT NULL
  AND start_date > CURRENT_DATE;

-- 修复状态为 completed 但实际日期范围包含今天的 Sprint（被错误标记已完成）
-- 仅修复没有任何已关闭工单的 Sprint，且同项目中没有其他 active Sprint（排他约束）
UPDATE sprint
SET status = 'active', updated_at = NOW()
WHERE status = 'completed'
  AND start_date IS NOT NULL
  AND end_date IS NOT NULL
  AND start_date <= CURRENT_DATE
  AND end_date >= CURRENT_DATE
  AND id NOT IN (
    SELECT DISTINCT i.sprint_id
    FROM issue i
    JOIN issue_status ist ON ist.id = i.status_id
    WHERE i.sprint_id IS NOT NULL
      AND i.deleted_at IS NULL
      AND ist.is_closed = true
  )
  AND NOT EXISTS (
    SELECT 1 FROM sprint s2
    WHERE s2.project_id = sprint.project_id
      AND s2.status = 'active'
      AND s2.id != sprint.id
  );

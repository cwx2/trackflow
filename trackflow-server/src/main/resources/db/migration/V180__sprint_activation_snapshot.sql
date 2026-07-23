-- V180__sprint_activation_snapshot.sql
-- Sprint 激活时记录估算快照，作为燃尽图基线（参考 YouTrack "Start Sprint" 行为）

ALTER TABLE sprint ADD COLUMN started_at TIMESTAMP;
ALTER TABLE sprint ADD COLUMN start_scope_hours DECIMAL(10,2);
ALTER TABLE sprint ADD COLUMN start_scope_issues INTEGER;

COMMENT ON COLUMN sprint.started_at IS 'Sprint 实际激活时间（区别于计划开始日期 start_date）';
COMMENT ON COLUMN sprint.start_scope_hours IS '激活时的总预估工时快照（所有工单 estimated_hours 之和）';
COMMENT ON COLUMN sprint.start_scope_issues IS '激活时的工单数量快照';

-- 回填已激活/已完成的 Sprint：使用 start_date 作为 started_at 的近似值
UPDATE sprint
SET started_at = start_date::timestamp,
    start_scope_issues = (
        SELECT COUNT(*)
        FROM issue
        WHERE issue.sprint_id = sprint.id
          AND issue.deleted_at IS NULL
    ),
    start_scope_hours = (
        SELECT COALESCE(SUM(issue.estimated_hours), 0)
        FROM issue
        WHERE issue.sprint_id = sprint.id
          AND issue.deleted_at IS NULL
    )
WHERE status IN ('active', 'completed');

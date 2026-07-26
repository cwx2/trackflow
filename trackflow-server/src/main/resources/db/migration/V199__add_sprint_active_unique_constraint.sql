-- V199__add_sprint_active_unique_constraint.sql
-- 为 sprint 表添加部分唯一索引，确保每个项目同时只有一个 active Sprint
-- 修复 TOCTOU 竞态条件：应用层 SELECT COUNT 检查在 READ COMMITTED 隔离级别下无法防止并发激活
-- 参见 REQ-582

DO $$
BEGIN
    -- 先检查是否已存在该索引
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'sprint'
          AND indexname = 'idx_sprint_project_active_unique'
    ) THEN
        CREATE UNIQUE INDEX idx_sprint_project_active_unique
        ON sprint(project_id)
        WHERE status = 'active';
    END IF;
END $$;

COMMENT ON INDEX idx_sprint_project_active_unique IS
    'Scrum 排他约束：确保每个项目同时只有一个 active Sprint，防止并发激活的 TOCTOU 竞态条件';

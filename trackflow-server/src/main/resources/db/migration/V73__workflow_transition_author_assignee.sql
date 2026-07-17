-- ============================================================
-- V73: 为 workflow_transition 表添加 author/assignee 维度
-- 参考 OpenProject: db/migrate/tables/workflows.rb
--
-- 语义：
--   author=false, assignee=false → 通用规则（任何人适用）
--   author=true → 仅工单创建者适用
--   assignee=true → 仅工单负责人适用
--   两者可叠加（author=true AND assignee=true → 创建者或负责人适用）
-- ============================================================

-- 1. 新增布尔字段（默认 false，向后兼容现有数据）
-- 使用 DO 块实现幂等（列已存在时跳过）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'workflow_transition' AND column_name = 'author') THEN
        ALTER TABLE workflow_transition ADD COLUMN author BOOLEAN NOT NULL DEFAULT false;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'workflow_transition' AND column_name = 'assignee') THEN
        ALTER TABLE workflow_transition ADD COLUMN assignee BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;

-- 2. 删除旧唯一约束（不含 author/assignee）
ALTER TABLE workflow_transition DROP CONSTRAINT IF EXISTS workflow_transition_project_id_issue_type_role_id_old_statu_key;

-- 3. 创建新的唯一索引（包含 author/assignee 维度）
-- NULLS NOT DISTINCT 确保 project_id=NULL 的行也能正确去重
DROP INDEX IF EXISTS workflow_transition_unique;
CREATE UNIQUE INDEX workflow_transition_unique
    ON workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
    NULLS NOT DISTINCT;

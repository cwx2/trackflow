-- =============================================================
-- V142: 更新 workflow_transition 唯一约束以包含 workflow_definition_id
--
-- 旧约束：(project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
-- 新约束：(workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
--
-- 理由：引入 workflow_definition 实体后，转换规则的唯一性由定义 ID 决定，
--       不再依赖 project_id（同一定义可被多项目共享，克隆后的规则属于新定义）。
-- =============================================================

-- 删除旧的唯一约束
DROP INDEX IF EXISTS workflow_transition_unique;

-- 创建新的唯一约束（基于 workflow_definition_id）
CREATE UNIQUE INDEX workflow_transition_unique
ON workflow_transition (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
NULLS NOT DISTINCT;

-- V259__workflow_rule_action_type.sql
-- 扩展 workflow_rule 表支持 Action Rule 类型（用户触发的命令规则）
-- 对标 YouTrack Workflow Constructor 的 Action Rule 功能

-- 新增 action_command 字段：Action Rule 的命令名（全局唯一）
ALTER TABLE workflow_rule ADD COLUMN IF NOT EXISTS action_command VARCHAR(100);

-- action_command 全局唯一约束（只对非空值生效）
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_rule_action_command
    ON workflow_rule(action_command) WHERE action_command IS NOT NULL;

-- 扩展 name 列长度以支持更长的规则名
ALTER TABLE workflow_rule ALTER COLUMN name TYPE VARCHAR(200);

-- 添加注释
COMMENT ON COLUMN workflow_rule.action_command IS 'Action Rule 的命令名（如 task/take/clone），全局唯一';

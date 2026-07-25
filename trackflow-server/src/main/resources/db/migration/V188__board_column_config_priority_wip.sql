-- V188__board_column_config_priority_wip.sql
-- 支持优先级模式看板列的 WIP 配置存储。
-- 修改 board_column_config 表，允许 status_id 为 NULL（用于优先级模式列），
-- 并添加基于 field_value 的复合唯一约束。

-- 1. 允许 status_id 为 NULL
ALTER TABLE board_column_config ALTER COLUMN status_id DROP NOT NULL;

-- 2. 旧的 (project_id, status_id) 唯一约束仅适用于 status 模式（status_id NOT NULL 的行）
--    需要改造为：status 模式按 (project_id, status_id) 唯一，priority 模式按 (project_id, field_value) 唯一

-- 删除旧的唯一约束
ALTER TABLE board_column_config DROP CONSTRAINT IF EXISTS board_column_config_project_id_status_id_key;

-- 添加新的部分唯一索引
-- status 模式行（status_id 非 null）：(project_id, status_id) 唯一
CREATE UNIQUE INDEX uq_board_column_config_status
    ON board_column_config (project_id, status_id)
    WHERE status_id IS NOT NULL;

-- priority 模式行（status_id 为 null，field_value 非 null）：(project_id, field_value) 唯一
CREATE UNIQUE INDEX uq_board_column_config_priority
    ON board_column_config (project_id, field_value)
    WHERE status_id IS NULL AND field_value IS NOT NULL;

COMMENT ON COLUMN board_column_config.status_id IS
    'status 模式列 ID（来自 issue_status 表）；priority 模式列时为 NULL';
COMMENT ON COLUMN board_column_config.field_value IS
    'status 模式：等同 status_id 的字符串形式；priority 模式：优先级值（Critical/High/Normal/Low）';

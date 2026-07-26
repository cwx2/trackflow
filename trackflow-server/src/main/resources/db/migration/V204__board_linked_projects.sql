-- V204__board_linked_projects.sql
-- 为看板基本设置表添加关联项目字段，支持一块看板关联多个项目（跨项目看板）

ALTER TABLE board_general_config
    ADD COLUMN IF NOT EXISTS linked_project_ids JSONB DEFAULT '[]'::jsonb;

COMMENT ON COLUMN board_general_config.linked_project_ids IS
    '关联项目 ID 列表（JSONB 数组），跨项目看板时存储额外关联的项目 ID。'
    '主项目已通过 project_id 字段确定，此字段仅存储附加项目。';

-- 颜色方案支持按项目着色：在 board_card_config 中添加 color_scheme 为 project 的处理
-- （board_card_config 表的 color_scheme 字段已存在，无需新增列；此处只添加索引以备查询）
CREATE INDEX IF NOT EXISTS idx_board_general_config_project_id ON board_general_config(project_id);

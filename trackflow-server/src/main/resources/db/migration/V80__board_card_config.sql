-- 看板卡片配置表：存储每个项目看板卡片的显示字段和颜色方案
CREATE TABLE board_card_config (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    -- 卡片上显示的字段列表（JSON 数组，如 ["assignee","priority","type","tags","dueDate","sprint","estimatedHours"]）
    visible_fields  JSONB NOT NULL DEFAULT '["assignee","priority","type"]'::jsonb,
    -- 颜色方案：none / priority / type / project
    color_scheme    VARCHAR(32) NOT NULL DEFAULT 'none',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id)
);

CREATE INDEX idx_board_card_config_project ON board_card_config(project_id);

COMMENT ON TABLE board_card_config IS '看板卡片配置：项目维度的卡片显示字段和颜色方案设置';
COMMENT ON COLUMN board_card_config.visible_fields IS '卡片上显示的字段列表（JSON 数组）';
COMMENT ON COLUMN board_card_config.color_scheme IS '卡片颜色方案：none=无着色, priority=按优先级, type=按类型, project=按项目';

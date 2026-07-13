-- 看板列配置表：存储每个项目的看板显示哪些状态列
CREATE TABLE board_column_config (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    status_id   BIGINT NOT NULL REFERENCES issue_status(id) ON DELETE CASCADE,
    visible     BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    collapsed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, status_id)
);

CREATE INDEX idx_board_column_config_project ON board_column_config(project_id);

COMMENT ON TABLE board_column_config IS '看板列配置：项目维度的状态列显示/隐藏/排序设置';
COMMENT ON COLUMN board_column_config.visible IS '是否在看板中显示此状态列';
COMMENT ON COLUMN board_column_config.collapsed IS '列是否默认折叠（用于有工单但用户选择折叠的情况）';
COMMENT ON COLUMN board_column_config.sort_order IS '列在看板中的显示顺序';

-- 看板泳道配置表：存储每个项目的 Swimlane 分组字段设置
CREATE TABLE board_swimlane_config (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    -- 泳道分组字段：none / assignee / priority / type / sprint / tag
    group_by_field  VARCHAR(32) NOT NULL DEFAULT 'none',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id)
);

CREATE INDEX idx_board_swimlane_config_project ON board_swimlane_config(project_id);

COMMENT ON TABLE board_swimlane_config IS '看板泳道配置：项目维度的 Swimlane 分组字段设置';
COMMENT ON COLUMN board_swimlane_config.group_by_field IS '泳道分组字段：none=无泳道, assignee=按负责人, priority=按优先级, type=按类型, sprint=按迭代, tag=按标签';

-- 看板列合并配置表：存储列合并关系（多个状态合并显示为一列）
-- 设计：一个 merge_group_id 代表一组合并的列，用自定义标题展示
CREATE TABLE board_column_merge (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    -- 合并组标识（同一组内的状态合并显示为同一列）
    merge_group_id  VARCHAR(64) NOT NULL,
    -- 合并组显示标题（如"待办"可以包含"待处理"+"重新打开"两个状态）
    merge_title     VARCHAR(100) NOT NULL,
    -- 被合并的状态 ID
    status_id       BIGINT NOT NULL REFERENCES issue_status(id) ON DELETE CASCADE,
    -- 在合并组内的排序（影响拖入时默认选择哪个状态）
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, status_id)
);

CREATE INDEX idx_board_column_merge_project ON board_column_merge(project_id);
CREATE INDEX idx_board_column_merge_group ON board_column_merge(project_id, merge_group_id);

COMMENT ON TABLE board_column_merge IS '看板列合并配置：将多个状态列合并显示为同一列';
COMMENT ON COLUMN board_column_merge.merge_group_id IS '合并组标识，同一个 group_id 内的状态显示为同一列';
COMMENT ON COLUMN board_column_merge.merge_title IS '合并后的列标题（自定义）';
COMMENT ON COLUMN board_column_merge.status_id IS '被合并的状态 ID';
COMMENT ON COLUMN board_column_merge.sort_order IS '组内排序，拖入合并列时按此顺序选择目标状态';

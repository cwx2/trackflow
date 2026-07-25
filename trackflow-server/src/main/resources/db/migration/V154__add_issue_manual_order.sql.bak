-- V154__add_issue_manual_order.sql
-- 支持工单列表手动排序（拖拽排序 / Manual Order），参考 OpenProject ordered_work_packages 设计

CREATE TABLE issue_manual_order (
    id          BIGSERIAL PRIMARY KEY,
    context_type VARCHAR(20) NOT NULL,  -- 'project' or 'query'
    context_id  BIGINT NOT NULL,        -- project_id or saved_query_id
    user_id     BIGINT,                 -- NULL = owner's global order; NOT NULL = user's personal order
    issue_id    BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- 唯一约束：同一个上下文 + 用户维度内，每个 issue 只能出现一次
CREATE UNIQUE INDEX uk_manual_order_context_issue
    ON issue_manual_order(context_type, context_id, COALESCE(user_id, 0), issue_id);

-- 查询索引：按上下文+用户查询并按 position 排序
CREATE INDEX idx_manual_order_context_user_position
    ON issue_manual_order(context_type, context_id, COALESCE(user_id, 0), position);

-- issue 维度索引：删除 issue 时级联清理
CREATE INDEX idx_manual_order_issue_id ON issue_manual_order(issue_id);

COMMENT ON TABLE issue_manual_order IS '工单手动排序表，存储用户对工单列表的自定义拖拽排序';
COMMENT ON COLUMN issue_manual_order.context_type IS '排序上下文类型：project=项目维度, query=保存查询维度';
COMMENT ON COLUMN issue_manual_order.context_id IS '上下文 ID：project_id 或 saved_query_id';
COMMENT ON COLUMN issue_manual_order.user_id IS '用户 ID，NULL 表示所有者设置的全局排序，非 NULL 表示个人排序';
COMMENT ON COLUMN issue_manual_order.issue_id IS '工单 ID';
COMMENT ON COLUMN issue_manual_order.position IS '排序位置，从 0 开始';

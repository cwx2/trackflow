-- 看板 Board Behavior：新增"按查询过滤"模式
-- filter_mode 增加 'query' 选项
-- filter_query 存储 JSON 格式的筛选条件（与 saved_query.filters 相同格式）

-- 1. 删除旧的 CHECK 约束，替换为支持 'query' 的新约束
ALTER TABLE board_general_config
    DROP CONSTRAINT IF EXISTS chk_board_filter_mode;

ALTER TABLE board_general_config
    ADD CONSTRAINT chk_board_filter_mode CHECK (filter_mode IN ('all', 'active_sprint', 'query'));

-- 2. 新增 filter_query 列（TEXT 类型，存储 JSON 数组格式的筛选条件）
ALTER TABLE board_general_config
    ADD COLUMN filter_query TEXT DEFAULT NULL;

COMMENT ON COLUMN board_general_config.filter_query IS '查询过滤条件（JSON数组），当 filter_mode=query 时生效。格式与 saved_query.filters 一致';

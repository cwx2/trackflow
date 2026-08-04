-- V180__workflow_transition_name_and_initial.sql
-- 为 workflow_transition 表增加转换命名和初始状态标记字段
-- 转换命名：用于工单详情页状态下拉展示名（如"开始处理"代替目标状态名）
-- 初始状态：标记新建该类型工单时默认进入的状态

ALTER TABLE workflow_transition
    ADD COLUMN transition_name VARCHAR(100),
    ADD COLUMN is_initial BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN workflow_transition.transition_name IS '转换显示名（如"开始处理"），为空时使用目标状态名';
COMMENT ON COLUMN workflow_transition.is_initial IS '是否标记为初始状态（新建工单默认状态）';

-- 扩展 transition_action 表的 action_type CHECK 约束（如果存在的话先删除）
-- 允许 auto_assign, add_comment, require_field, add_tag 四种动作类型
DO $$
BEGIN
    -- 删除旧的 CHECK 约束（如果存在）
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'transition_action'
          AND constraint_type = 'CHECK'
          AND constraint_name = 'chk_action_type'
    ) THEN
        ALTER TABLE transition_action DROP CONSTRAINT chk_action_type;
    END IF;
END $$;

ALTER TABLE transition_action
    ADD CONSTRAINT chk_action_type CHECK (
        action_type IN ('auto_assign', 'add_comment', 'require_field', 'add_tag')
    );

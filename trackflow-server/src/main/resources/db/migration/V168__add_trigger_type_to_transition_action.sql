-- V166__add_trigger_type_to_transition_action.sql
-- 为 transition_action 表增加 trigger_type 列，支持 onEnter/onExit 语义
-- 对标 YouTrack 状态机的 onEnter/onExit 钩子能力

-- 新增 trigger_type 列：'transition'(默认,现有语义) / 'on_enter' / 'on_exit'
ALTER TABLE transition_action ADD COLUMN trigger_type VARCHAR(20) NOT NULL DEFAULT 'transition';

-- 添加注释
COMMENT ON COLUMN transition_action.trigger_type IS '触发类型：transition(精确路径触发,默认) / on_enter(进入状态触发) / on_exit(离开状态触发)';

-- 修改 new_status_id 列允许为 NULL（on_exit 规则需要 new_status_id 为 NULL）
ALTER TABLE transition_action ALTER COLUMN new_status_id DROP NOT NULL;

-- 添加约束：确保 trigger_type 值合法
ALTER TABLE transition_action ADD CONSTRAINT chk_trigger_type
    CHECK (trigger_type IN ('transition', 'on_enter', 'on_exit'));

-- 添加约束：on_enter 规则必须 old_status_id 为 NULL，new_status_id 不为 NULL
-- on_exit 规则必须 old_status_id 不为 NULL，new_status_id 为 NULL
-- transition 规则必须 old_status_id 不为 NULL，new_status_id 不为 NULL（或 old_status_id 为 NULL 表示创建时触发）
ALTER TABLE transition_action ADD CONSTRAINT chk_trigger_type_status_ids
    CHECK (
        (trigger_type = 'transition') OR
        (trigger_type = 'on_enter' AND old_status_id IS NULL AND new_status_id IS NOT NULL) OR
        (trigger_type = 'on_exit' AND old_status_id IS NOT NULL AND new_status_id IS NULL)
    );

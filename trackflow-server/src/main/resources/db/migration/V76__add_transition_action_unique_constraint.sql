-- =====================================================
-- V76: transition_action 表添加唯一性约束
-- 防止同一转换路径上创建重复的同类型动作
-- =====================================================

-- 由于 project_id 和 old_status_id 可为 NULL，PostgreSQL 的 UNIQUE 约束中 NULL != NULL
-- 使用 COALESCE 将 NULL 转为哨兵值 0（不与真实 ID 冲突，因为 ID 从 1 开始）
CREATE UNIQUE INDEX uq_ta_path_type ON transition_action (
    COALESCE(project_id, 0),
    issue_type,
    COALESCE(old_status_id, 0),
    new_status_id,
    action_type
);

-- 添加注释说明约束含义
COMMENT ON INDEX uq_ta_path_type IS '同一转换路径（project_id + issue_type + old_status_id + new_status_id）下不允许存在相同 action_type 的重复动作';

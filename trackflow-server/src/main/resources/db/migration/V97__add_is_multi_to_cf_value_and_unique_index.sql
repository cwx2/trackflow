-- REQ-356: 为 custom_field_value 表添加 is_multi 标记列和条件唯一索引
-- 防止单值字段在并发场景下产生重复记录

-- 1. 添加 is_multi 冗余列，标记该行属于多值字段还是单值字段
ALTER TABLE custom_field_value ADD COLUMN is_multi BOOLEAN NOT NULL DEFAULT false;

-- 2. 根据 custom_field_definition 的 is_multi 值，更新已有数据
UPDATE custom_field_value cfv
SET is_multi = true
FROM custom_field_definition cfd
WHERE cfv.custom_field_id = cfd.id AND cfd.is_multi = true;

-- 3. 清理可能已存在的重复记录（保留最新的一条）
-- 仅针对单值字段（is_multi=false），如果存在重复的 (issue_id, custom_field_id) 组合
DELETE FROM custom_field_value cfv
WHERE cfv.is_multi = false
AND cfv.id NOT IN (
    SELECT DISTINCT ON (issue_id, custom_field_id) id
    FROM custom_field_value
    WHERE is_multi = false
    ORDER BY issue_id, custom_field_id, updated_at DESC
);

-- 4. 创建条件唯一索引：单值字段同一 (issue_id, custom_field_id) 只能有一行
CREATE UNIQUE INDEX uk_cf_value_single
ON custom_field_value (issue_id, custom_field_id)
WHERE is_multi = false;

-- 5. 为多值字段保留查询索引
CREATE INDEX idx_cf_value_issue_field_multi
ON custom_field_value (issue_id, custom_field_id)
WHERE is_multi = true;

-- 6. 移除旧的普通索引（已被上面两个条件索引覆盖）
DROP INDEX IF EXISTS idx_cf_value_issue_field;

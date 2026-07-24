-- V184__add_cf_option_active_value_unique_constraint.sql
-- 为 custom_field_option 表添加非归档选项的 (custom_field_id, value) 唯一约束
-- 防止同一枚举字段出现重复的活跃选项名称，与 YouTrack 行为保持一致

-- 1. 清理已有重复数据（如有）：保留 position 最小的，归档其余重复项
-- 先标记需要归档的重复选项（保留每组中 position 最小的记录）
UPDATE custom_field_option
SET is_archived = true, updated_at = NOW()
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY custom_field_id, value ORDER BY position ASC, id ASC) as rn
        FROM custom_field_option
        WHERE is_archived = false
    ) ranked
    WHERE rn > 1
);

-- 2. 创建 partial unique index（仅对非归档选项生效）
CREATE UNIQUE INDEX uq_cf_option_field_value_active
ON custom_field_option(custom_field_id, value)
WHERE is_archived = false;

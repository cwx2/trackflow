-- ============================================================
-- REQ-214: 列表类型选项归档支持
-- 已被工单引用的选项不再物理删除，改为归档（可展示但不可选择新值）
-- ============================================================

ALTER TABLE custom_field_option
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN custom_field_option.is_archived IS '是否已归档。归档选项不出现在新值选择列表中，但已引用的值仍可正确展示';

-- 索引优化：查询未归档选项时使用
CREATE INDEX idx_cf_option_active ON custom_field_option(custom_field_id, is_archived) WHERE is_archived = false;

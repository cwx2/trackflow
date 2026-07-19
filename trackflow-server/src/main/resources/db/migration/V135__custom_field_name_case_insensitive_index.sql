-- ============================================================
-- 修复 custom_field_definition.name 唯一索引为 case-insensitive
-- 原索引 uk_custom_field_name 使用 btree(name) 是 case-sensitive 的，
-- 而 Service 层用 LOWER(name) 做重复检查，导致语义不一致。
-- 改为表达式索引 LOWER(name) 确保 DB 层也是 case-insensitive。
-- ============================================================

-- 删除旧的 case-sensitive 唯一约束
ALTER TABLE custom_field_definition DROP CONSTRAINT IF EXISTS uk_custom_field_name;

-- 创建新的 case-insensitive 唯一索引
CREATE UNIQUE INDEX uk_custom_field_name ON custom_field_definition(LOWER(name));

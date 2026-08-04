-- V256__seed_version_custom_fields.sql
-- 1. 更新 CHECK 约束以允许 'version' 类型
-- 2. 预置 "Fix versions" 和 "Affected versions" 两个 version 类型的全局字段

-- 先删除旧约束，再重建包含 'version' 的新约束
ALTER TABLE custom_field_definition DROP CONSTRAINT IF EXISTS ck_custom_field_format;
ALTER TABLE custom_field_definition ADD CONSTRAINT ck_custom_field_format
    CHECK (field_format IN ('string','text','int','float','date','datetime','bool','list','user','period','state','ownedField','version'));

-- Fix versions（修复版本）
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, default_value, min_length, max_length, regexp, position, is_multi, is_hidden_in_list, aliases, is_private, is_auto_attach, sort_mode, created_at, updated_at)
VALUES (1000000000000000005, 'Fix versions', 'version', false, true, NULL, 0, 0, NULL, 5, true, false, '修复版本,fix version', false, false, 'manual', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Affected versions（受影响版本）
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, default_value, min_length, max_length, regexp, position, is_multi, is_hidden_in_list, aliases, is_private, is_auto_attach, sort_mode, created_at, updated_at)
VALUES (1000000000000000006, 'Affected versions', 'version', false, true, NULL, 0, 0, NULL, 6, true, false, '受影响版本,affected version', false, false, 'manual', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

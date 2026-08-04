-- V256__seed_version_custom_fields.sql
-- 预置 "Fix versions" 和 "Affected versions" 两个 version 类型的全局字段。
-- 使用固定 ID 以便系统识别为内置字段。

-- Fix versions（修复版本）
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, default_value, min_length, max_length, regexp, position, is_multi, is_hidden_in_list, aliases, is_private, is_auto_attach, sort_mode, created_at, updated_at)
VALUES (1000000000000000005, 'Fix versions', 'version', false, true, NULL, 0, 0, NULL, 5, true, false, '修复版本,fix version', false, false, 'manual', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Affected versions（受影响版本）
INSERT INTO custom_field_definition (id, name, field_format, is_required, is_for_all, default_value, min_length, max_length, regexp, position, is_multi, is_hidden_in_list, aliases, is_private, is_auto_attach, sort_mode, created_at, updated_at)
VALUES (1000000000000000006, 'Affected versions', 'version', false, true, NULL, 0, 0, NULL, 6, true, false, '受影响版本,affected version', false, false, 'manual', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

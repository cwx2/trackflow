-- V254__add_owned_field_type.sql
-- 新增 ownedField 类型支持：为自定义字段选项添加 owner_user_id 列
-- ownedField 类似 list 类型但每个选项可指定一个负责人（owner）
-- 典型用途：Subsystem（子系统）字段，每个组件有一个负责人

-- 1. 更新 field_format 检查约束，增加 ownedField 类型
ALTER TABLE custom_field_definition DROP CONSTRAINT ck_custom_field_format;
ALTER TABLE custom_field_definition ADD CONSTRAINT ck_custom_field_format
    CHECK (field_format IN ('string', 'text', 'int', 'float', 'date', 'datetime', 'bool', 'list', 'user', 'period', 'state', 'ownedField'));

-- 2. 为 custom_field_option 添加 owner_user_id 列
ALTER TABLE custom_field_option ADD COLUMN owner_user_id BIGINT REFERENCES sys_user(id) ON DELETE SET NULL;

COMMENT ON COLUMN custom_field_option.owner_user_id IS '选项负责人（仅 ownedField 类型使用），指向 sys_user';

-- 3. 预置全局 Subsystem 字段（ownedField 类型，非自动附加）
INSERT INTO custom_field_definition (name, field_format, is_required, is_for_all, default_value, min_length, max_length, position, is_multi, is_hidden_in_list, is_private, is_auto_attach, sort_mode, created_at, updated_at)
VALUES ('Subsystem', 'ownedField', false, false, NULL, 0, 0,
        (SELECT COALESCE(MAX(position), 0) + 1 FROM custom_field_definition),
        false, false, false, false, 'manual', NOW(), NOW())
ON CONFLICT DO NOTHING;

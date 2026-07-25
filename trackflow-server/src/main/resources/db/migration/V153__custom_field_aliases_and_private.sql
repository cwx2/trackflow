-- V153__custom_field_aliases_and_private.sql
-- 为自定义字段增加别名（aliases）和全局隐私标记（is_private）功能。
-- 别名允许用户在搜索/命令中使用简写引用字段。
-- 隐私标记限制字段仅对具有特定权限的用户可见/可编辑。

-- 1. 新增列
ALTER TABLE custom_field_definition ADD COLUMN aliases VARCHAR(512);
COMMENT ON COLUMN custom_field_definition.aliases IS '字段别名（JSON数组），可用于搜索/命令中替代字段名';

ALTER TABLE custom_field_definition ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT false;
COMMENT ON COLUMN custom_field_definition.is_private IS '全局隐私标记，true时仅具有 read_private_fields 权限的用户可见';

-- 2. 新增权限节点
INSERT INTO sys_permission (id, code, name, category, scope, description, sort_order, enabled, created_at)
VALUES
  (nextval('sys_permission_id_seq'), 'issue:read_private_fields', '查看隐私字段', 'issue', 'project',
   '允许查看标记为"隐私"的自定义字段值', 130, true, NOW()),
  (nextval('sys_permission_id_seq'), 'issue:update_private_fields', '编辑隐私字段', 'issue', 'project',
   '允许编辑标记为"隐私"的自定义字段值', 131, true, NOW())
ON CONFLICT DO NOTHING;

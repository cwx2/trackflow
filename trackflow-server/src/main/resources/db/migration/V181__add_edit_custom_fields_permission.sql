-- V181__add_edit_custom_fields_permission.sql
-- 新增 issue:edit_custom_fields 权限，允许特定角色编辑自定义字段而无需整体 issue:edit 权限。
-- 主要解决测试人员无法编辑测试相关自定义字段的问题（REQ-322）。

-- 1. 新增权限定义
INSERT INTO sys_permission (code, name, category, scope, description, sort_order, enabled)
VALUES ('issue:edit_custom_fields', '编辑自定义字段', 'issue', 'project', '允许编辑工单的自定义字段值（不含核心字段如类型、优先级等）', 35, true)
ON CONFLICT (code) DO NOTHING;

-- 2. 给测试人员角色(id=4)授予该权限
INSERT INTO role_permission (role_id, permission)
VALUES (4, 'issue:edit_custom_fields')
ON CONFLICT (role_id, permission) DO NOTHING;

-- 3. 给产品经理角色(id=6)也授予该权限（产品经理也需要编辑自定义字段）
INSERT INTO role_permission (role_id, permission)
VALUES (6, 'issue:edit_custom_fields')
ON CONFLICT (role_id, permission) DO NOTHING;

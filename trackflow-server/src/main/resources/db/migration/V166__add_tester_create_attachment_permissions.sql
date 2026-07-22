-- V166__add_tester_create_attachment_permissions.sql
-- 为测试人员角色(role_id=4)添加 issue:create 和 issue:manage_attachments 权限
-- 测试人员需要创建 Bug 工单并附带截图，这两个权限是测试工作流的基本要求
-- 参考 YouTrack：所有非只读角色（Contributor 及以上）默认拥有 Create Issue 权限

INSERT INTO role_permission (role_id, permission)
VALUES (4, 'issue:create')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
VALUES (4, 'issue:manage_attachments')
ON CONFLICT DO NOTHING;

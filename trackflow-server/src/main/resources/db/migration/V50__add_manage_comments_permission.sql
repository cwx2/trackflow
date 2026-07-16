-- V50: Add issue:manage_comments permission (edit/delete others' comments)
-- Assign to system_admin (role_id=1), project_admin (role_id=2), and tech_lead (role_id=7)

INSERT INTO sys_permission (code, name, category, scope, description, sort_order)
VALUES ('issue:manage_comments', '管理评论', 'issue', 'project', '编辑和删除他人的评论', 8)
ON CONFLICT (code) DO NOTHING;

-- Grant to system_admin (role_id=1)
INSERT INTO role_permission (role_id, permission)
VALUES (1, 'issue:manage_comments')
ON CONFLICT (role_id, permission) DO NOTHING;

-- Grant to project_admin (role_id=2)
INSERT INTO role_permission (role_id, permission)
VALUES (2, 'issue:manage_comments')
ON CONFLICT (role_id, permission) DO NOTHING;

-- Grant to tech_lead (role_id=7)
INSERT INTO role_permission (role_id, permission)
VALUES (7, 'issue:manage_comments')
ON CONFLICT (role_id, permission) DO NOTHING;

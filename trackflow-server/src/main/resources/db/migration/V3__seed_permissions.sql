-- ============================================================
-- 内置角色默认权限
-- ============================================================

-- 系统管理员：拥有所有权限（通过 system:admin 标识，代码中做 shortcut 检查）
INSERT INTO role_permission (role_id, permission) VALUES
(1, 'system:admin');

-- 项目管理员
INSERT INTO role_permission (role_id, permission) VALUES
(2, 'project:create'),
(2, 'project:edit'),
(2, 'project:delete'),
(2, 'project:view'),
(2, 'project:manage_members'),
(2, 'project:manage_workflow'),
(2, 'project:manage_custom_fields'),
(2, 'issue:create'),
(2, 'issue:view'),
(2, 'issue:edit'),
(2, 'issue:delete'),
(2, 'issue:assign'),
(2, 'issue:change_status'),
(2, 'issue:comment'),
(2, 'issue:manage_attachments'),
(2, 'sprint:create'),
(2, 'sprint:edit'),
(2, 'sprint:delete'),
(2, 'sprint:view'),
(2, 'query:create'),
(2, 'query:share'),
(2, 'query:manage_public'),
(2, 'report:view'),
(2, 'report:create'),
(2, 'webhook:manage');

-- 开发人员
INSERT INTO role_permission (role_id, permission) VALUES
(3, 'project:view'),
(3, 'issue:create'),
(3, 'issue:view'),
(3, 'issue:edit'),
(3, 'issue:assign'),
(3, 'issue:change_status'),
(3, 'issue:comment'),
(3, 'issue:manage_attachments'),
(3, 'sprint:view'),
(3, 'query:create'),
(3, 'report:view');

-- 测试人员
INSERT INTO role_permission (role_id, permission) VALUES
(4, 'project:view'),
(4, 'issue:view'),
(4, 'issue:comment'),
(4, 'issue:change_status'),
(4, 'sprint:view'),
(4, 'query:create'),
(4, 'report:view');

-- 观察者
INSERT INTO role_permission (role_id, permission) VALUES
(5, 'project:view'),
(5, 'issue:view'),
(5, 'issue:comment'),
(5, 'query:create'),
(5, 'report:view');

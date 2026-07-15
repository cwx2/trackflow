-- ============================================================
-- REQ-55: NonMember / Anonymous 访问控制层
-- 1. project 表增加 visibility 字段
-- 2. 新增 NonMember 和 Anonymous 内置角色
-- 3. 为 NonMember 和 Anonymous 分配默认权限
-- ============================================================

-- 1. 项目可见性字段（默认 private，不改变现有行为）
ALTER TABLE project ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'private';
COMMENT ON COLUMN project.visibility IS '项目可见性: private(仅成员可见), internal(所有登录用户可见), public(未登录也可见)';

CREATE INDEX idx_project_visibility ON project(visibility);

-- 2. 新增内置角色：NonMember (已登录但非成员) 和 Anonymous (未登录)
INSERT INTO sys_role (id, name, code, description, role_type, builtin, sort_order) VALUES
(8, '非成员', 'non_member', '登录用户访问 internal/public 项目时的默认角色', 'project', true, 8),
(9, '匿名用户', 'anonymous', '未登录用户访问 public 项目时的默认角色', 'project', true, 9);

-- 3. NonMember 默认权限（只读 + 评论）
INSERT INTO role_permission (role_id, permission) VALUES
(8, 'project:view'),
(8, 'issue:view'),
(8, 'issue:comment'),
(8, 'sprint:view'),
(8, 'query:create'),
(8, 'report:view');

-- 4. Anonymous 默认权限（仅只读，不可评论）
INSERT INTO role_permission (role_id, permission) VALUES
(9, 'project:view'),
(9, 'issue:view'),
(9, 'sprint:view'),
(9, 'report:view');

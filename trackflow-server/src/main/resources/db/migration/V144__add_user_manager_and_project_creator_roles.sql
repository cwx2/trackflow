-- =============================================================================
-- V143: 新增「用户管理员」和「项目创建者」全局角色
--
-- 对标 YouTrack 的 User Manager / Project Creator 预定义角色，
-- 实现最小权限原则：允许将用户管理和项目创建权限委派给非系统管理员。
--
-- 参考：https://www.jetbrains.com/help/youtrack/server/default-roles.html
-- =============================================================================

-- 1. 新增全局角色
INSERT INTO sys_role (id, name, code, description, role_type, builtin, sort_order, created_at, updated_at) VALUES
    (10, '用户管理员', 'user_manager', '管理用户和用户组，不能管理角色定义或系统配置', 'global', true, 10, NOW(), NOW()),
    (11, '项目创建者', 'project_creator', '可创建新项目并自动成为项目管理员', 'global', true, 11, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. 用户管理员权限：manage_users + manage_groups
INSERT INTO role_permission (role_id, permission) VALUES
    (10, 'system:manage_users'),
    (10, 'system:manage_groups')
ON CONFLICT DO NOTHING;

-- 3. 项目创建者权限：project:create
INSERT INTO role_permission (role_id, permission) VALUES
    (11, 'project:create')
ON CONFLICT DO NOTHING;

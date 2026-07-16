-- =============================================================================
-- V57: 为系统管理员角色补充全局管理细粒度权限
-- =============================================================================
-- 问题：system_admin 角色仅持有 'system:admin'（通行证），
--       导致无法创建"只能管理用户但不能管理角色"的细粒度管理员角色。
--       虽然 PermissionService 中 system:admin 会绕过一切检查，
--       但为了完整性和前端权限面板的正确展示，需要显式种入。
-- 影响：system_admin 现在在权限面板中显示所有系统管理权限（只读）
-- =============================================================================

-- 为 system_admin (id=1) 补充所有全局管理权限
INSERT INTO role_permission (role_id, permission) VALUES
    (1, 'system:manage_users'),
    (1, 'system:manage_roles'),
    (1, 'system:manage_orgs')
ON CONFLICT DO NOTHING;

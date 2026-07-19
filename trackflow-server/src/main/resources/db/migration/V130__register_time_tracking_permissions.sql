-- ============================================================================
-- V130: Register time:* permissions to sys_permission table
--
-- These 5 permissions already exist in role_permission (assigned to roles)
-- but were never registered in sys_permission (the permission registry).
-- This causes them to be invisible in the admin UI and unusable as API Key scopes.
-- ============================================================================

INSERT INTO sys_permission (code, name, category, scope, description, sort_order, enabled)
VALUES
    ('time:log',             '记录工时',       'time_tracking', 'project', '为自己记录工时',                     26, true),
    ('time:view_others',     '查看他人工时',   'time_tracking', 'project', '查看项目内其他成员的工时记录',       27, true),
    ('time:edit_all',        '编辑所有工时',   'time_tracking', 'project', '编辑项目内任何成员的工时记录',       28, true),
    ('time:delete_all',      '删除所有工时',   'time_tracking', 'project', '删除项目内任何成员的工时记录',       29, true),
    ('time:log_for_others',  '代他人记录工时', 'time_tracking', 'project', '为项目内其他成员记录工时',           30, true)
ON CONFLICT DO NOTHING;

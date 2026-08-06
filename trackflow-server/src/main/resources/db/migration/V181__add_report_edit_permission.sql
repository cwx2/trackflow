-- V181__add_report_edit_permission.sql
-- 为报表模块增加 report:edit 权限码，区分查看和编辑操作
-- 之前 update/delete/share 操作错误使用 canViewReports()（report:view），
-- 现在改为使用 canEditReports()（report:edit）

INSERT INTO sys_permission (code, name, description)
VALUES ('report:edit', '编辑报表', '编辑、删除、管理报表共享')
ON CONFLICT (code) DO NOTHING;

-- 将 report:edit 权限分配给已有 report:create 的角色（产品经理、项目管理员）
INSERT INTO role_permission (role_id, permission)
SELECT rp.role_id, 'report:edit'
FROM role_permission rp
WHERE rp.permission = 'report:create'
ON CONFLICT (role_id, permission) DO NOTHING;

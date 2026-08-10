-- V289: 为产品经理角色补充报表权限
-- product_manager (role_id=6) 缺少 report:view、report:create、report:edit 权限，
-- 导致产品经理无法访问报表页面（前端路由返回 403）。
-- 原本由 V26 和 V264 迁移分配，但后续通过 RoleService.replacePermissions API
-- 被覆盖丢失。本迁移恢复这些权限。
-- REQ-485

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.perm
FROM sys_role r
CROSS JOIN (VALUES ('report:view'), ('report:create'), ('report:edit')) AS p(perm)
WHERE r.code = 'product_manager'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id = r.id AND rp.permission = p.perm
  )

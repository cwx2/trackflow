-- 为 product_manager 角色添加 Sprint 查看和创建权限
-- 产品经理需要参与 Sprint 规划，应能查看和创建 Sprint
-- 参考 YouTrack：Agile Board 的项目选择器按权限过滤，product_manager 可参与规划

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'sprint:view'
FROM sys_role r
WHERE r.code = 'product_manager'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id = r.id AND rp.permission = 'sprint:view'
  );

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'sprint:create'
FROM sys_role r
WHERE r.code = 'product_manager'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id = r.id AND rp.permission = 'sprint:create'
  );

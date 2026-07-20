-- V140: 新增资源级细粒度权限（issue:edit_own, issue:edit_assigned）
-- 将硬编码的 reporter/assignee 隐式编辑规则改为可配置的权限码

-- 1. 新增权限定义
INSERT INTO sys_permission (code, name, category, scope, description, sort_order, enabled)
VALUES
    ('issue:edit_own', '编辑自己创建的工单', 'issue', 'project',
     '当用户是工单的创建者时，允许编辑该工单（即使没有 issue:edit 全量权限）', 10, true),
    ('issue:edit_assigned', '编辑分配给自己的工单', 'issue', 'project',
     '当用户是工单的负责人时，允许编辑该工单和变更状态（即使没有 issue:edit 全量权限）', 11, true)
ON CONFLICT (code) DO NOTHING;

-- 2. 为有编辑能力的角色默认开启这两个权限
-- project_admin(2), developer(3), tester(4), product_manager(6), tech_lead(7) 默认开启
-- observer(5) 默认不开启——这是关键区别
INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.code
FROM sys_role r
CROSS JOIN (VALUES ('issue:edit_own'), ('issue:edit_assigned')) AS p(code)
WHERE r.code IN ('project_admin', 'developer', 'tester', 'product_manager', 'tech_lead')
ON CONFLICT (role_id, permission) DO NOTHING;

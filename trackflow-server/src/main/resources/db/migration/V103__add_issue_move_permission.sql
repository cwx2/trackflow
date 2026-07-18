-- V103: Add issue:move permission for moving issues between projects
-- Required by REQ-394-1

-- 1. Add permission definition
INSERT INTO sys_permission (code, name, category, scope, description, sort_order)
VALUES ('issue:move', '移动工单', 'issue', 'project', '将工单移动到其他项目', 9)
ON CONFLICT DO NOTHING;

-- 2. Grant to project_admin (role_id=2)
INSERT INTO role_permission (role_id, permission)
SELECT 2, 'issue:move'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permission WHERE role_id = 2 AND permission = 'issue:move'
);

-- 3. Grant to tech_lead (role_id=7)
INSERT INTO role_permission (role_id, permission)
SELECT 7, 'issue:move'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permission WHERE role_id = 7 AND permission = 'issue:move'
);

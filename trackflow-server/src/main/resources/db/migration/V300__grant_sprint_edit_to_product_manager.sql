-- Grant sprint:edit permission to product_manager role
-- Product managers need to edit Sprint goal, name, and dates during Sprint planning.
-- They already have sprint:create and sprint:view; sprint:edit is required for inline goal editing.

INSERT INTO role_permission (role_id, permission)
SELECT id, 'sprint:edit'
FROM sys_role
WHERE code = 'product_manager'
ON CONFLICT DO NOTHING;

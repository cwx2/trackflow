-- V68: Add time:log permission for creating/updating/deleting time entries
-- YouTrack equivalent: "Create Work Item" permission
-- Roles that get this permission: system_admin (1), project_admin (2), developer (3), tester (4), product_manager (6), tech_lead (7)
-- Roles that do NOT get this: observer (5), non_member (8), anonymous (9)

INSERT INTO role_permission (role_id, permission)
VALUES
    (1, 'time:log'),
    (2, 'time:log'),
    (3, 'time:log'),
    (4, 'time:log'),
    (6, 'time:log'),
    (7, 'time:log')
ON CONFLICT DO NOTHING;

-- V66: Add time:view_others permission for viewing other users' time entries
-- Assigned to: system_admin (role_id=1), project_admin (role_id=2), tech_lead (role_id=7)

INSERT INTO role_permission (role_id, permission) VALUES
(1, 'time:view_others'),
(2, 'time:view_others'),
(7, 'time:view_others')
ON CONFLICT DO NOTHING;

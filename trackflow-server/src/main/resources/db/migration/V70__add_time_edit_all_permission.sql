-- V70: Add time:edit_all and time:delete_all permissions for managing others' time entries
-- Reference: OpenProject edit_time_entries permission (allows editing/deleting all users' time entries)
-- Roles that get these permissions:
--   system_admin (1) - full access
--   project_admin (2) - project-level management
--   tech_lead (7) - team lead oversight

INSERT INTO role_permission (role_id, permission) VALUES
(1, 'time:edit_all'),
(2, 'time:edit_all'),
(7, 'time:edit_all'),
(1, 'time:delete_all'),
(2, 'time:delete_all'),
(7, 'time:delete_all')
ON CONFLICT DO NOTHING;

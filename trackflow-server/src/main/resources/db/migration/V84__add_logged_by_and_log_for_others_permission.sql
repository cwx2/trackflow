-- V84: Add logged_by column to time_entry and time:log_for_others permission
-- Reference: OpenProject time_entry.logged_by + log_time permission (for logging on behalf of others)
-- This enables project managers / tech leads to record time on behalf of team members

-- 1. Add logged_by column (who performed the logging action)
ALTER TABLE time_entry ADD COLUMN logged_by BIGINT;

-- 2. Backfill: all existing entries were self-logged
UPDATE time_entry SET logged_by = user_id;

-- 3. Make NOT NULL after backfill
ALTER TABLE time_entry ALTER COLUMN logged_by SET NOT NULL;

-- 4. Add FK constraint
ALTER TABLE time_entry ADD CONSTRAINT fk_time_entry_logged_by
    FOREIGN KEY (logged_by) REFERENCES sys_user(id);

-- 5. Add index for querying "entries logged by user X"
CREATE INDEX idx_time_entry_logged_by ON time_entry(logged_by);

-- 6. Add time:log_for_others permission
-- Roles: system_admin (1), project_admin (2), tech_lead (7)
-- These roles can record time on behalf of other project members
INSERT INTO role_permission (role_id, permission) VALUES
    (1, 'time:log_for_others'),
    (2, 'time:log_for_others'),
    (7, 'time:log_for_others')
ON CONFLICT DO NOTHING;

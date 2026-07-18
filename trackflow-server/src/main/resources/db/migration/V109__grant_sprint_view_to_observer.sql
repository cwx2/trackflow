-- Grant sprint:view permission to observer role
-- Fix: Observer (role_id=5) was missing sprint:view, while non_member and anonymous both have it.
-- This caused 403 when observers tried to view Sprint pages or use Sprint filter in issue list.
-- Reference: YouTrack allows observers with "Read Project" permission to view Sprints (read-only).

INSERT INTO role_permission (role_id, permission)
VALUES (5, 'sprint:view')
ON CONFLICT DO NOTHING;

-- REQ-427: Add workflow transitions for tech_lead role from Testing status
-- tech_lead (role_id=7) currently has no transitions from Testing (status_id=4),
-- making the status field read-only after they push an issue to Testing.
-- This adds: Testing → Done, Testing → Cancelled, Testing → Needs Fix
-- matching the project_admin pattern for this status.

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, conditions, author, assignee, workflow_definition_id, require_comment, transition_name, is_initial)
VALUES
    (NULL, '*', 7, 4, 5, '{}', false, false, 1, false, NULL, false),
    (NULL, '*', 7, 4, 6, '{}', false, false, 1, false, NULL, false),
    (NULL, '*', 7, 4, 19, '{}', false, false, 1, false, NULL, false)
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

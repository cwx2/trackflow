-- REQ-725: Add workflow transitions for tech_lead role from Todo and UI Todo statuses
-- tech_lead (role_id=7) has transitions from Open, In Progress, Code Review, Testing,
-- Reopened, and Needs Fix — but is missing transitions from Todo (8) and UI Todo (9).
-- This mirrors the developer role's transitions from these statuses.
-- Todo (8) → In Progress (2), Cancelled (6)
-- UI Todo (9) → In Progress (2), Todo (8)

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, conditions, author, assignee, workflow_definition_id, require_comment, transition_name, is_initial)
VALUES
    (NULL, '*', 7, 8, 2, '{}', false, false, 1, false, NULL, false),
    (NULL, '*', 7, 8, 6, '{}', false, false, 1, false, NULL, false),
    (NULL, '*', 7, 9, 2, '{}', false, false, 1, false, NULL, false),
    (NULL, '*', 7, 9, 8, '{}', false, false, 1, false, NULL, false)
ON CONFLICT (workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee) DO NOTHING;

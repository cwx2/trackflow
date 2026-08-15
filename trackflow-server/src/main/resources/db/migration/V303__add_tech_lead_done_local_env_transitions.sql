-- Add workflow transitions for tech_lead (role_id=7) from "Done (Local Env)" (status_id=10)
-- Fixes REQ-739: tech_lead has no available transitions from this status, blocking Code Review flow
-- Transitions added:
--   Done (Local Env) -> Pending Code Review (12)
--   Done (Local Env) -> Testing (4)
--   Done (Local Env) -> In Progress (2) — reject/rework path

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES
    (NULL, '*', 7, 10, 12, false, false),
    (NULL, '*', 7, 10, 4,  false, false),
    (NULL, '*', 7, 10, 2,  false, false)
ON CONFLICT DO NOTHING;

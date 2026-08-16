-- REQ-762: Add workflow transitions for tech_lead (role_id=7) from "Pending Code Review" (status_id=12)
-- Fixes: tech_lead has no available transitions from "Pending Code Review" status,
-- blocking the Code Review workflow — tech_lead cannot push issues to Testing after review
-- or reject back to In Progress.
-- Transitions added:
--   Pending Code Review (12) -> Testing (4)        — code review passed, move to testing
--   Pending Code Review (12) -> In Progress (2)    — code review failed, return to developer
--   Pending Code Review (12) -> Done (Local Env) (10) — optional: mark as locally done

INSERT INTO workflow_transition (project_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
VALUES
    (NULL, '*', 7, 12, 4,  false, false),
    (NULL, '*', 7, 12, 2,  false, false),
    (NULL, '*', 7, 12, 10, false, false)
ON CONFLICT DO NOTHING;

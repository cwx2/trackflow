-- REQ-447: Add missing workflow transitions for tester role from Reopened status
-- Tester (role_id=4) currently can only go from Reopened to Cancelled (with author constraint),
-- which means non-author testers have zero available transitions from Reopened.
-- Other roles (project_admin, tech_lead, developer, product_manager) all have Reopened→In Progress.

-- Reopened(7) → In Progress(2) for tester, no author/assignee constraint
INSERT INTO workflow_transition (old_status_id, new_status_id, role_id, issue_type, project_id, author, assignee)
VALUES (7, 2, 4, '*', NULL, false, false)
ON CONFLICT DO NOTHING;

-- Reopened(7) → Testing(4) for tester, no author/assignee constraint
INSERT INTO workflow_transition (old_status_id, new_status_id, role_id, issue_type, project_id, author, assignee)
VALUES (7, 4, 4, '*', NULL, false, false)
ON CONFLICT DO NOTHING;

-- Reopened(7) → Done(5) for tester, no author/assignee constraint
INSERT INTO workflow_transition (old_status_id, new_status_id, role_id, issue_type, project_id, author, assignee)
VALUES (7, 5, 4, '*', NULL, false, false)
ON CONFLICT DO NOTHING;

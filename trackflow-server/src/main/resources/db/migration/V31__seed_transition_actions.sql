-- V31: Seed global default auto-assign rules for common status transitions
-- These are global defaults (project_id = NULL) applicable to all issue types (issue_type = '*')
-- Projects can override these by creating project-specific rules

-- Rule 1: In Progress → Testing: auto-assign to tester role (round_robin)
-- When a developer finishes coding and moves the issue to Testing,
-- automatically assign it to a tester using round-robin rotation.
INSERT INTO transition_action (
    project_id, issue_type, old_status_id, new_status_id,
    action_type, action_config, sort_order, enabled, created_at, updated_at
)
SELECT
    NULL,
    '*',
    old_s.id,
    new_s.id,
    'auto_assign',
    '{"strategy":"role_based","role_id":4,"mode":"round_robin"}'::jsonb,
    0,
    true,
    NOW(),
    NOW()
FROM issue_status old_s, issue_status new_s
WHERE old_s.code = 'in_progress'
  AND new_s.code = 'testing'
  AND NOT EXISTS (
      SELECT 1 FROM transition_action ta
      WHERE ta.project_id IS NULL
        AND ta.issue_type = '*'
        AND ta.old_status_id = old_s.id
        AND ta.new_status_id = new_s.id
        AND ta.action_type = 'auto_assign'
  );

-- Rule 2: Testing → In Progress: auto-assign to previous_assignee (return to developer)
-- When a tester finds a bug and rejects the issue back to In Progress,
-- automatically re-assign it to the developer who was working on it before.
INSERT INTO transition_action (
    project_id, issue_type, old_status_id, new_status_id,
    action_type, action_config, sort_order, enabled, created_at, updated_at
)
SELECT
    NULL,
    '*',
    old_s.id,
    new_s.id,
    'auto_assign',
    '{"strategy":"previous_assignee","fallback_strategy":"reporter"}'::jsonb,
    0,
    true,
    NOW(),
    NOW()
FROM issue_status old_s, issue_status new_s
WHERE old_s.code = 'testing'
  AND new_s.code = 'in_progress'
  AND NOT EXISTS (
      SELECT 1 FROM transition_action ta
      WHERE ta.project_id IS NULL
        AND ta.issue_type = '*'
        AND ta.old_status_id = old_s.id
        AND ta.new_status_id = new_s.id
        AND ta.action_type = 'auto_assign'
  );

-- Rule 3: Resolved → Reopened: auto-assign to previous_assignee (return to developer)
-- When a resolved issue is reopened, automatically re-assign it to the
-- developer who last worked on it, falling back to reporter if no history.
INSERT INTO transition_action (
    project_id, issue_type, old_status_id, new_status_id,
    action_type, action_config, sort_order, enabled, created_at, updated_at
)
SELECT
    NULL,
    '*',
    old_s.id,
    new_s.id,
    'auto_assign',
    '{"strategy":"previous_assignee","fallback_strategy":"reporter"}'::jsonb,
    0,
    true,
    NOW(),
    NOW()
FROM issue_status old_s, issue_status new_s
WHERE old_s.code = 'resolved'
  AND new_s.code = 'reopened'
  AND NOT EXISTS (
      SELECT 1 FROM transition_action ta
      WHERE ta.project_id IS NULL
        AND ta.issue_type = '*'
        AND ta.old_status_id = old_s.id
        AND ta.new_status_id = new_s.id
        AND ta.action_type = 'auto_assign'
  );

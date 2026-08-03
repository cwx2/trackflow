-- V175: Add auto-assign rule for Testing → Needs Fix transition
-- When a tester rejects an issue to "Needs Fix" status, automatically
-- re-assign it to the previous developer (who was working on it before testing).
-- This mirrors the existing Testing → In Progress rule (V31).

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
  AND new_s.code = 'needs_fix'
  AND NOT EXISTS (
      SELECT 1 FROM transition_action ta
      WHERE ta.project_id IS NULL
        AND ta.issue_type = '*'
        AND ta.old_status_id = old_s.id
        AND ta.new_status_id = new_s.id
        AND ta.action_type = 'auto_assign'
  );

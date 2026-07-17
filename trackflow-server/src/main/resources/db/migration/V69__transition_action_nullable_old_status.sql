-- V69: Allow old_status_id to be NULL in transition_action table.
-- NULL old_status_id means the action triggers on issue creation (no prior status → default status).
-- This enables "auto-assign on create" rules reusing the existing TransitionActionEngine.

-- 1. Make old_status_id nullable
ALTER TABLE transition_action ALTER COLUMN old_status_id DROP NOT NULL;

-- 2. Add a comment explaining the semantics
COMMENT ON COLUMN transition_action.old_status_id IS 'Source status ID. NULL means "on creation" (no prior status → initial status).';

-- 3. Add index for creation-path queries (old_status_id IS NULL)
CREATE INDEX idx_ta_creation_path ON transition_action (project_id, issue_type, new_status_id)
    WHERE old_status_id IS NULL AND enabled = true;

-- 4. Seed a global "auto-assign on create" rule:
--    When an issue is created (old_status=NULL, new_status=Open/id=1), assign to a developer via role_based (round_robin).
--    This applies to all issue types (*) globally (project_id=NULL).
INSERT INTO transition_action (project_id, issue_type, old_status_id, new_status_id, action_type, action_config, sort_order, enabled, created_at, updated_at)
VALUES (NULL, '*', NULL, 1, 'auto_assign',
        '{"strategy": "role_based", "role_id": 3, "mode": "round_robin", "fallback_strategy": "project_lead"}'::jsonb,
        0, true, NOW(), NOW())
ON CONFLICT DO NOTHING;

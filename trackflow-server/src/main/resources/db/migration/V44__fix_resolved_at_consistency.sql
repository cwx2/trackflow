-- V44: Fix resolved_at field consistency
-- REQ-78: resolved_at was not cleared when issues were reopened,
-- and seed data for closed issues never set resolved_at.

-- Fix 1: Closed issues missing resolved_at → set to updated_at
UPDATE issue SET resolved_at = updated_at
WHERE status_id IN (SELECT id FROM issue_status WHERE is_closed = true)
  AND resolved_at IS NULL
  AND deleted_at IS NULL;

-- Fix 2: Non-closed issues with stale resolved_at → clear it
UPDATE issue SET resolved_at = NULL
WHERE status_id NOT IN (SELECT id FROM issue_status WHERE is_closed = true)
  AND resolved_at IS NOT NULL
  AND deleted_at IS NULL;

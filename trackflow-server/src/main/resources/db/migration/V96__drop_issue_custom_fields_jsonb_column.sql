-- V96: Drop deprecated issue.custom_fields JSONB column
-- This column was deprecated in V62 and replaced by the custom_field_value EAV table.
-- The application has not read/written this column since the EAV migration.
-- Historical data (8 rows) is stale and inconsistent with EAV values — safe to discard.

-- Drop the GIN index first
DROP INDEX IF EXISTS idx_issue_custom_fields;

-- Drop the column
ALTER TABLE issue DROP COLUMN IF EXISTS custom_fields;

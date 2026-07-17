-- V62: Deprecate issue.custom_fields JSONB column
-- The custom_field_value EAV table is now the single source of truth.
-- This column is no longer written to or read from by the application.
-- It is retained temporarily for rollback safety; will be dropped in a future migration.

COMMENT ON COLUMN issue.custom_fields IS 'DEPRECATED: No longer used. Custom field values are stored in custom_field_value table (EAV). Will be dropped in a future release.';

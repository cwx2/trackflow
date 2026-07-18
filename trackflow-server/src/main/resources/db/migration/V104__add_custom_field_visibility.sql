-- V39: Add role-based field visibility and editability to custom_field_project
-- Implements Group-based Field Visibility (similar to YouTrack Advanced Settings)

-- visible_to_roles: JSONB array of role IDs (e.g. [2,7]). NULL = visible to all.
-- updatable_by_roles: JSONB array of role IDs. NULL = updatable by all who can see it.
ALTER TABLE custom_field_project
    ADD COLUMN visible_to_roles JSONB DEFAULT NULL,
    ADD COLUMN updatable_by_roles JSONB DEFAULT NULL;

COMMENT ON COLUMN custom_field_project.visible_to_roles IS 'Role IDs that can see this field. NULL = visible to all project members.';
COMMENT ON COLUMN custom_field_project.updatable_by_roles IS 'Role IDs that can edit this field. NULL = editable by all who can see it.';

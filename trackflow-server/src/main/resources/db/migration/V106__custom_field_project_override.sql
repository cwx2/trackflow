-- V106: Add project-level override for is_required and default_value in custom_field_project
-- Allows each project to independently configure whether a field is required and its default value,
-- overriding the global setting from custom_field_definition.
-- NULL means "inherit from global setting", non-null means "project-level override".

ALTER TABLE custom_field_project
    ADD COLUMN is_required BOOLEAN DEFAULT NULL,
    ADD COLUMN default_value TEXT DEFAULT NULL;

COMMENT ON COLUMN custom_field_project.is_required IS 'Project-level override for field required status. NULL = inherit global, true/false = override.';
COMMENT ON COLUMN custom_field_project.default_value IS 'Project-level override for field default value. NULL = inherit global.';

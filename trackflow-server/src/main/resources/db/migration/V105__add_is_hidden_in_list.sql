-- V40: Add is_hidden_in_list flag to custom_field_definition
-- Allows admins to hide custom fields from the default issue list column selector
-- (YouTrack "Hide from Issues list" equivalent)

ALTER TABLE custom_field_definition
    ADD COLUMN is_hidden_in_list BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN custom_field_definition.is_hidden_in_list IS 'When true, this field is hidden from the default issue list column selector. Users can still manually add it via personal settings.';

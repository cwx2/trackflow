-- V87: Add 'code' column to work_item_attribute for identifying builtin attributes by code
-- instead of relying on hardcoded id=1

ALTER TABLE work_item_attribute ADD COLUMN code VARCHAR(50);

-- Set the code for the existing builtin Work Type attribute
UPDATE work_item_attribute SET code = 'WORK_TYPE' WHERE is_builtin = true AND name = 'Work type';

-- Add unique constraint on code (only non-null values)
CREATE UNIQUE INDEX uk_work_item_attribute_code ON work_item_attribute (code) WHERE code IS NOT NULL;

COMMENT ON COLUMN work_item_attribute.code IS 'System code for builtin attributes (e.g. WORK_TYPE). NULL for user-created attributes.';

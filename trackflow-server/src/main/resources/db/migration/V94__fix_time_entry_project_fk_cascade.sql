-- V94: Fix time_entry.project_id FK to use ON DELETE CASCADE
-- Problem: fk_time_entry_project was created with default NO ACTION in V85,
-- causing project deletion to fail when time entries exist.
-- All other project FK constraints use CASCADE; this was the only outlier.
-- Also: time_entry_attribute_value → time_entry already uses CASCADE,
-- so deleting time_entry records will automatically clean up attribute values.

ALTER TABLE time_entry DROP CONSTRAINT fk_time_entry_project;
ALTER TABLE time_entry ADD CONSTRAINT fk_time_entry_project
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

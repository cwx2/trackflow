-- REQ-292: Add project_id to time_entry for decoupling from issue table
-- This allows time entries to survive issue soft-deletion

-- 1. Add project_id column (nullable initially for backfill)
ALTER TABLE time_entry ADD COLUMN project_id BIGINT;

-- 2. Backfill project_id from issue table (including soft-deleted issues)
UPDATE time_entry te
SET project_id = i.project_id
FROM issue i
WHERE i.id = te.issue_id;

-- 3. Make project_id NOT NULL after backfill
ALTER TABLE time_entry ALTER COLUMN project_id SET NOT NULL;

-- 4. Add index for project-based queries
CREATE INDEX idx_time_entry_project_workdate ON time_entry(project_id, work_date);

-- 5. Add foreign key constraint
ALTER TABLE time_entry ADD CONSTRAINT fk_time_entry_project
    FOREIGN KEY (project_id) REFERENCES project(id);

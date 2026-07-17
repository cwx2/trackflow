-- V88: Add ongoing timer support to time_entry
-- Allows a time_entry to be in "ongoing" state (timer running)
-- Only one ongoing entry per user is allowed (partial unique index)
-- Reference: OpenProject time_entries table with ongoing field + partial unique index

-- 1. Add ongoing column
ALTER TABLE time_entry ADD COLUMN ongoing BOOLEAN NOT NULL DEFAULT false;

-- 2. Make duration nullable (ongoing=true entries have no duration until stopped)
ALTER TABLE time_entry ALTER COLUMN duration DROP NOT NULL;

-- 3. Partial unique index: each user can have at most one ongoing timer
CREATE UNIQUE INDEX idx_time_entry_user_ongoing ON time_entry(user_id) WHERE ongoing = true;

-- 4. Index for querying active timers
CREATE INDEX idx_time_entry_ongoing ON time_entry(ongoing) WHERE ongoing = true;

-- 5. Check constraint: non-ongoing entries must have duration
ALTER TABLE time_entry ADD CONSTRAINT chk_time_entry_duration_when_not_ongoing
    CHECK (ongoing = true OR duration IS NOT NULL);

COMMENT ON COLUMN time_entry.ongoing IS '是否正在计时（true=计时器进行中，duration 为 NULL）';

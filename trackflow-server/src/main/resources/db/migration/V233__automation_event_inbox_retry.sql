ALTER TABLE automation_event_inbox
    ADD COLUMN IF NOT EXISTS attempt INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE automation_event_inbox
    ADD CONSTRAINT automation_event_inbox_attempt_check
        CHECK (attempt >= 0 AND max_attempts BETWEEN 1 AND 100);

DROP INDEX IF EXISTS idx_automation_event_unprocessed;
CREATE INDEX idx_automation_event_unprocessed
    ON automation_event_inbox(available_at, occurred_at)
    WHERE processed_at IS NULL;

COMMENT ON COLUMN automation_event_inbox.attempt IS '事件路由尝试次数';
COMMENT ON COLUMN automation_event_inbox.available_at IS '失败退避后的下次可处理时间';

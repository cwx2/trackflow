-- V127: Add on_issue_updated preference column for generic field change notifications.
-- Covers: priority, due_date, description, sprint, parent, tags changes.
-- Default TRUE so existing users receive notifications for field updates.

ALTER TABLE notification_preference
    ADD COLUMN IF NOT EXISTS on_issue_updated BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN notification_preference.on_issue_updated IS
    'Subscribe to issue field update notifications (priority, due_date, description, sprint, parent, tags)';

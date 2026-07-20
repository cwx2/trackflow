-- Per-event email channel control for notification preferences
-- Allows users to independently choose which event types also trigger email notifications
-- Global email_enabled remains as the master switch; per-event controls provide granularity

ALTER TABLE notification_preference
    ADD COLUMN email_on_issue_assigned     BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_issue_status_changed BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_issue_commented    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN email_on_mentioned          BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_issue_resolved     BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_issue_updated      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN email_on_sprint_started     BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN email_on_sprint_completed   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN email_on_project_member_changed BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_project_lifecycle  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN email_on_due_date           BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_overdue            BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN email_on_watched_updated    BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN notification_preference.email_on_issue_assigned IS 'Email when an issue is assigned to me';
COMMENT ON COLUMN notification_preference.email_on_issue_status_changed IS 'Email when issue status changes';
COMMENT ON COLUMN notification_preference.email_on_issue_commented IS 'Email when issue gets a new comment';
COMMENT ON COLUMN notification_preference.email_on_mentioned IS 'Email when @mentioned in a comment';
COMMENT ON COLUMN notification_preference.email_on_issue_resolved IS 'Email when my reported issue is resolved';
COMMENT ON COLUMN notification_preference.email_on_issue_updated IS 'Email when issue fields are updated';
COMMENT ON COLUMN notification_preference.email_on_sprint_started IS 'Email when sprint starts';
COMMENT ON COLUMN notification_preference.email_on_sprint_completed IS 'Email when sprint completes';
COMMENT ON COLUMN notification_preference.email_on_project_member_changed IS 'Email on project member changes';
COMMENT ON COLUMN notification_preference.email_on_project_lifecycle IS 'Email on project archive/restore';
COMMENT ON COLUMN notification_preference.email_on_due_date IS 'Email on due date approaching';
COMMENT ON COLUMN notification_preference.email_on_overdue IS 'Email on overdue alerts';
COMMENT ON COLUMN notification_preference.email_on_watched_updated IS 'Email when watched issues are updated';

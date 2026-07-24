-- V157__add_voted_spent_time_preference_fields.sql
-- 为 notification_preference 表新增投票和工时通知的偏好字段。
-- 对标 YouTrack 完整订阅事件列表（8 种），补充 onVoted 和 onSpentTime 事件。

ALTER TABLE notification_preference ADD COLUMN IF NOT EXISTS on_issue_voted BOOLEAN;
ALTER TABLE notification_preference ADD COLUMN IF NOT EXISTS email_on_issue_voted BOOLEAN;
ALTER TABLE notification_preference ADD COLUMN IF NOT EXISTS on_issue_spent_time BOOLEAN;
ALTER TABLE notification_preference ADD COLUMN IF NOT EXISTS email_on_issue_spent_time BOOLEAN;

COMMENT ON COLUMN notification_preference.on_issue_voted IS '工单被投票时通知（站内）';
COMMENT ON COLUMN notification_preference.email_on_issue_voted IS '工单被投票时通知（邮件）';
COMMENT ON COLUMN notification_preference.on_issue_spent_time IS '工单有工时记录变更时通知（站内）';
COMMENT ON COLUMN notification_preference.email_on_issue_spent_time IS '工单有工时记录变更时通知（邮件）';

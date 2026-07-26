-- V196__fix_notification_preference_voted_spent_time_defaults.sql
-- 修复 notification_preference 中 issue_voted/spent_time 相关字段的 NULL 值
-- 对标 YouTrack 标准：站内通知默认启用，邮件通知默认关闭

-- 1. 将存量 NULL 记录更新为明确的默认值
UPDATE notification_preference
SET
    on_issue_voted = true,
    email_on_issue_voted = false,
    on_issue_spent_time = true,
    email_on_issue_spent_time = false
WHERE on_issue_voted IS NULL
   OR email_on_issue_voted IS NULL
   OR on_issue_spent_time IS NULL
   OR email_on_issue_spent_time IS NULL;

-- 2. 为字段设置 NOT NULL 约束和列默认值，防止未来再次出现 NULL
ALTER TABLE notification_preference
    ALTER COLUMN on_issue_voted SET DEFAULT true,
    ALTER COLUMN on_issue_voted SET NOT NULL,
    ALTER COLUMN email_on_issue_voted SET DEFAULT false,
    ALTER COLUMN email_on_issue_voted SET NOT NULL,
    ALTER COLUMN on_issue_spent_time SET DEFAULT true,
    ALTER COLUMN on_issue_spent_time SET NOT NULL,
    ALTER COLUMN email_on_issue_spent_time SET DEFAULT false,
    ALTER COLUMN email_on_issue_spent_time SET NOT NULL;

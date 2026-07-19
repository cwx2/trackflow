-- V129: 通知邮件延迟发送支持
-- 新增 mail_sent 字段追踪邮件是否已发送，mail_sent_at 记录发送时间。
-- 参考 OpenProject Notification 表的 mail_alert_sent 字段。

ALTER TABLE notification ADD COLUMN mail_sent BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notification ADD COLUMN mail_sent_at TIMESTAMP;

-- 已有记录标记为 mail_sent=true（历史通知不需要再发邮件）
UPDATE notification SET mail_sent = true;

-- 索引：用于定时任务查找待发邮件的通知
-- 条件：mail_sent=false AND is_read=false（聚合窗口结束后仍未读的需要发邮件）
CREATE INDEX idx_notification_mail_pending
    ON notification (created_at)
    WHERE mail_sent = false AND is_read = false;

COMMENT ON COLUMN notification.mail_sent IS '邮件是否已发送（延迟投递模型）';
COMMENT ON COLUMN notification.mail_sent_at IS '邮件发送时间';

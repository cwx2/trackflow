-- V92: 通知表增加 reason 列（"为什么通知我"维度）
-- 参考 OpenProject Notification#reason 枚举设计
-- REQ-315

ALTER TABLE notification ADD COLUMN reason VARCHAR(30);

-- 为已有数据根据 type 推断 reason（尽力而为，不精确）
UPDATE notification SET reason = 'assigned' WHERE type = 'issue_assigned';
UPDATE notification SET reason = 'assigned' WHERE type = 'issue_auto_assigned';
UPDATE notification SET reason = 'mentioned' WHERE type = 'mention';
-- issue_commented / issue_status_changed 的历史数据无法准确还原 reason，保留 NULL

COMMENT ON COLUMN notification.reason IS '通知原因：为什么通知此用户（assigned/reporter/commenter/mentioned/member/watched）';

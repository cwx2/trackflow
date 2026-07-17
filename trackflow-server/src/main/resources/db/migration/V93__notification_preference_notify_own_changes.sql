-- ============================================================
-- 通知偏好表新增 notify_own_changes 字段
-- 用户可配置是否接收自己操作触发的通知
-- 默认 FALSE（不接收，与 YouTrack 行为一致）
-- ============================================================

ALTER TABLE notification_preference ADD COLUMN notify_own_changes BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN notification_preference.notify_own_changes IS '是否接收自己操作触发的通知（默认关闭）';

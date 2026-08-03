-- V246__add_overdue_reminder_interval.sql
-- 为通知偏好表增加逾期提醒间隔配置字段
-- 解决：逾期工单每天重复发送通知导致通知中心被淹没（REQ-137）
-- 参考 OpenProject: DATE_ALERT_OVERDUE_DURATIONS = [nil, 1, 3, 7]

-- 逾期提醒间隔（天数数组），默认 [1,3,7,14] 表示在逾期第1天、第3天、第7天、第14天提醒
ALTER TABLE notification_preference
    ADD COLUMN overdue_reminder_days INTEGER[] DEFAULT '{1,3,7,14}';

COMMENT ON COLUMN notification_preference.overdue_reminder_days
    IS '逾期提醒间隔天数数组，仅在这些逾期天数时发送通知。默认[1,3,7,14]';

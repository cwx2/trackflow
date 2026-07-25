-- V194__fix_notification_subscription_events_add_voted_spent_time.sql
-- 为存量 notification_subscription 记录补充 onVoted 和 onSpentTime 事件键
-- 背景：buildDefaultEventsJson() 后续版本新增了这两个事件类型，但历史数据未迁移
-- 对不含这些键的记录添加默认值 true（与 buildDefaultEventsJson() 返回值一致）

UPDATE notification_subscription
SET events = events
    || CASE WHEN events->>'onVoted' IS NULL THEN '{"onVoted": true}'::jsonb ELSE '{}'::jsonb END
    || CASE WHEN events->>'onSpentTime' IS NULL THEN '{"onSpentTime": true}'::jsonb ELSE '{}'::jsonb END
WHERE events->>'onVoted' IS NULL
   OR events->>'onSpentTime' IS NULL;

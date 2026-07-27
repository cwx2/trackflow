-- V208__add_commented_by_me_default_subscription.sql
-- 为所有已有用户补全第三个内置订阅 "commented_by_me"（我评论过的工单）
-- 对标 YouTrack 标准：三个默认订阅 assigned_to_me / reported_by_me / commented_by_me
-- 仅为尚未拥有该订阅的用户创建，避免重复

INSERT INTO notification_subscription (user_id, name, source_type, builtin_key, is_default, events, created_at, updated_at)
SELECT
    u.id AS user_id,
    '我评论过的' AS name,
    'builtin' AS source_type,
    'commented_by_me' AS builtin_key,
    TRUE AS is_default,
    '{"onCreated":true,"onUpdated":true,"onResolved":true,"onCommented":true,"onTagAdded":true,"onTagRemoved":true,"onVoted":true,"onSpentTime":true}' AS events,
    NOW() AS created_at,
    NOW() AS updated_at
FROM sys_user u
WHERE NOT EXISTS (
      SELECT 1
      FROM notification_subscription ns
      WHERE ns.user_id = u.id
        AND ns.source_type = 'builtin'
        AND ns.builtin_key = 'commented_by_me'
  );

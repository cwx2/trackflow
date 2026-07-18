-- ============================================================
-- V110: 回填通知表 actor_id
--
-- 问题：历史通知记录中 78% 的 actor_id 为 NULL，
-- 导致通知面板无法显示"谁触发了这条通知"。
--
-- 策略：
-- 1. Issue 相关通知（issue_assigned/issue_status_changed/issue_commented/mention/issue_auto_assigned）：
--    从 issue_activity 表根据 issue_id + 时间窗口（±5秒）匹配 user_id 回填
-- 2. Project/Sprint 相关通知（member_added/role_changed/member_removed/lead_changed/
--    project_archived/project_restored/sprint_started/sprint_completed）：
--    无可靠关联表，保持 NULL，由前端容错处理
-- ============================================================

-- 步骤1：回填 issue 相关通知的 actor_id
-- 从 issue_activity 表中找到时间最接近的操作记录
UPDATE notification n
SET actor_id = matched.activity_user_id
FROM (
    SELECT DISTINCT ON (n2.id) n2.id AS notification_id, ia.user_id AS activity_user_id
    FROM notification n2
    JOIN issue_activity ia
        ON ia.issue_id = n2.resource_id
        AND ia.created_at BETWEEN n2.created_at - INTERVAL '5 seconds' AND n2.created_at + INTERVAL '5 seconds'
    WHERE n2.actor_id IS NULL
      AND n2.resource_type = 'issue'
      AND n2.type IN ('issue_assigned', 'issue_status_changed', 'issue_commented', 'mention', 'issue_auto_assigned', 'issue_moved')
    ORDER BY n2.id, ABS(EXTRACT(EPOCH FROM (ia.created_at - n2.created_at)))
) matched
WHERE n.id = matched.notification_id;

-- 步骤2：清理引用已删除 issue 的孤立通知记录
DELETE FROM notification
WHERE resource_type = 'issue'
  AND resource_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM issue WHERE id = notification.resource_id);

-- 步骤3：清理引用已删除 issue 的孤立 muted_thread 记录
DELETE FROM notification_muted_thread
WHERE resource_type = 'issue'
  AND resource_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM issue WHERE id = notification_muted_thread.resource_id);

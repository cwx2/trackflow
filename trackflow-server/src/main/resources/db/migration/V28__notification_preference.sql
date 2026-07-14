-- ============================================================
-- 通知偏好表：用户个人通知设置
-- ============================================================

CREATE TABLE notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,

    -- 事件订阅开关（站内通知渠道）
    on_issue_assigned BOOLEAN NOT NULL DEFAULT TRUE,         -- 工单分配给我
    on_issue_status_changed BOOLEAN NOT NULL DEFAULT TRUE,   -- 我参与的工单状态变更
    on_issue_commented BOOLEAN NOT NULL DEFAULT TRUE,        -- 我参与的工单有新评论
    on_mentioned BOOLEAN NOT NULL DEFAULT TRUE,              -- 评论中 @提及我
    on_issue_resolved BOOLEAN NOT NULL DEFAULT TRUE,         -- 我报告的工单被解决
    on_sprint_started BOOLEAN NOT NULL DEFAULT FALSE,        -- 所在项目有 Sprint 启动
    on_sprint_completed BOOLEAN NOT NULL DEFAULT FALSE,      -- 所在项目有 Sprint 完成

    -- 邮件通知渠道（预留，当前不实现邮件发送）
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- 静音时段（HH:mm 格式，null 表示不启用）
    quiet_hours_start VARCHAR(5),   -- 如 "22:00"
    quiet_hours_end VARCHAR(5),     -- 如 "08:00"

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 每个用户只有一条偏好记录
CREATE UNIQUE INDEX uk_notification_pref_user ON notification_preference(user_id);

-- 为所有已有用户插入默认偏好
INSERT INTO notification_preference (user_id)
SELECT id FROM sys_user;

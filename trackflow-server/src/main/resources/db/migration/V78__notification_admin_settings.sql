-- V78: 通知管理相关的系统设置种子数据
-- 使用 system_setting 表存储全局通知策略配置

INSERT INTO system_setting (setting_key, value, description, category)
VALUES
    ('notification.in_app_enabled', 'true', '站内通知全局开关', 'notification'),
    ('notification.email_enabled', 'false', '邮件通知全局开关（需配置 SMTP）', 'notification'),
    ('notification.retention_days', '90', '已读通知保留天数（0=永久保留）', 'notification'),
    ('notification.default_on_issue_assigned', 'true', '新用户默认偏好：工单被分配时通知', 'notification'),
    ('notification.default_on_issue_status_changed', 'true', '新用户默认偏好：工单状态变更时通知', 'notification'),
    ('notification.default_on_issue_commented', 'true', '新用户默认偏好：工单被评论时通知', 'notification'),
    ('notification.default_on_mentioned', 'true', '新用户默认偏好：被 @ 提及时通知', 'notification'),
    ('notification.default_on_issue_resolved', 'true', '新用户默认偏好：工单被解决时通知', 'notification'),
    ('notification.default_on_sprint_started', 'false', '新用户默认偏好：Sprint 开始时通知', 'notification'),
    ('notification.default_on_sprint_completed', 'false', '新用户默认偏好：Sprint 完成时通知', 'notification'),
    ('notification.default_on_project_member_changed', 'true', '新用户默认偏好：项目成员变更时通知', 'notification'),
    ('notification.default_on_project_lifecycle', 'true', '新用户默认偏好：项目生命周期变更时通知', 'notification'),
    ('notification.default_email_enabled', 'false', '新用户默认偏好：是否启用邮件通知', 'notification')
ON CONFLICT (setting_key) DO NOTHING;

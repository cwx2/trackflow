-- V151: 添加审计日志保留策略配置
-- 默认保留 180 天，管理员可通过系统设置调整

INSERT INTO system_setting (setting_key, value, description, category, created_at, updated_at)
VALUES ('audit_log.retention_days', '180', '审计日志保留天数（0=永久保留），超期记录由定时任务自动清理', 'audit', NOW(), NOW())
ON CONFLICT DO NOTHING;

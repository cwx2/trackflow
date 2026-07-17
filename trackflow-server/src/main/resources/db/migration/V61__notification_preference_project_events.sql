-- =====================================================
-- V61: 通知偏好表新增项目事件开关字段
-- REQ-204: 项目级通知绕过用户偏好设置
-- =====================================================

-- 成员增删/角色变更通知开关（默认开启）
ALTER TABLE notification_preference
    ADD COLUMN on_project_member_changed BOOLEAN NOT NULL DEFAULT true;

-- 项目生命周期通知开关（归档/恢复，默认开启）
ALTER TABLE notification_preference
    ADD COLUMN on_project_lifecycle BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN notification_preference.on_project_member_changed IS '成员增删/角色变更通知开关';
COMMENT ON COLUMN notification_preference.on_project_lifecycle IS '项目归档/恢复通知开关';

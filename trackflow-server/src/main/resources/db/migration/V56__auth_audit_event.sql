-- ============================================================
-- 扩展审计日志表以支持认证安全事件
-- 新增 user_agent 列用于记录客户端信息
-- 放宽 operator_id 和 target_id 的 NOT NULL 约束（认证失败时可能无用户）
-- ============================================================

-- 添加 user_agent 列
ALTER TABLE sys_audit_log ADD COLUMN user_agent VARCHAR(512);

-- 放宽 operator_id 约束：认证失败事件没有有效的用户 ID
ALTER TABLE sys_audit_log ALTER COLUMN operator_id DROP NOT NULL;

-- 放宽 target_id 约束：认证事件的 target 是用户自身，但失败事件可能无 target
ALTER TABLE sys_audit_log ALTER COLUMN target_id DROP NOT NULL;

-- 放宽 target_type 约束：认证事件统一用 'auth' 类型
ALTER TABLE sys_audit_log ALTER COLUMN target_type DROP NOT NULL;

COMMENT ON COLUMN sys_audit_log.user_agent IS '客户端 User-Agent（认证事件使用）';
COMMENT ON COLUMN sys_audit_log.action IS '操作类型：assign_global_role/remove_global_role/update_role_permissions/disable_user/enable_user/login/login_failed/first_login/api_key_used/api_key_failed';

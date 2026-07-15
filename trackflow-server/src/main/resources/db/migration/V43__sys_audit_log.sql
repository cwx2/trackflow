-- ============================================================
-- 系统级审计日志表：记录权限变更等敏感操作
-- ============================================================

CREATE TABLE sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES sys_user(id),      -- 执行操作的管理员
    action VARCHAR(50) NOT NULL,                              -- 操作类型
    target_type VARCHAR(50) NOT NULL,                         -- 目标类型: user, role
    target_id BIGINT NOT NULL,                                -- 目标 ID
    details JSONB,                                            -- 变更详情 {"old": ..., "new": ...}
    ip_address VARCHAR(45),                                   -- 操作者 IP（预留）
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引：按操作者查询
CREATE INDEX idx_audit_log_operator ON sys_audit_log(operator_id, created_at DESC);

-- 索引：按目标查询（如查某用户的所有权限变更历史）
CREATE INDEX idx_audit_log_target ON sys_audit_log(target_type, target_id, created_at DESC);

-- 索引：按操作类型查询
CREATE INDEX idx_audit_log_action ON sys_audit_log(action, created_at DESC);

-- 索引：按时间范围查询
CREATE INDEX idx_audit_log_time ON sys_audit_log(created_at DESC);

COMMENT ON TABLE sys_audit_log IS '系统审计日志（记录权限变更等敏感操作）';
COMMENT ON COLUMN sys_audit_log.action IS '操作类型：assign_global_role/remove_global_role/update_role_permissions/disable_user/enable_user';
COMMENT ON COLUMN sys_audit_log.target_type IS '目标实体类型：user/role';
COMMENT ON COLUMN sys_audit_log.details IS '变更详情 JSON，如 {"roleName":"system_admin"} 或 {"old":["p1","p2"],"new":["p1","p3"]}';

-- 用户禁用状态分类与原因
-- 支持区分离职/休假/安全封禁等不同禁用场景
ALTER TABLE sys_user ADD COLUMN ban_status VARCHAR(20);
ALTER TABLE sys_user ADD COLUMN ban_reason TEXT;
ALTER TABLE sys_user ADD COLUMN banned_at TIMESTAMP;
ALTER TABLE sys_user ADD COLUMN banned_by BIGINT;

-- ban_status 允许值：banned（封禁）/ suspended（暂停）/ inactive（不活跃）/ deactivated（注销）/ locked（锁定）
-- 当 status='active' 时，ban_status 应为 NULL

COMMENT ON COLUMN sys_user.ban_status IS '禁用状态分类: banned/suspended/inactive/deactivated/locked';
COMMENT ON COLUMN sys_user.ban_reason IS '禁用原因说明';
COMMENT ON COLUMN sys_user.banned_at IS '禁用操作时间';
COMMENT ON COLUMN sys_user.banned_by IS '执行禁用的管理员ID';

-- 为筛选查询添加索引
CREATE INDEX idx_sys_user_ban_status ON sys_user(ban_status) WHERE ban_status IS NOT NULL;

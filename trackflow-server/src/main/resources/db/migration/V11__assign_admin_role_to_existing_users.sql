-- ============================================================
-- 给所有已有用户分配系统管理员角色（内部系统，所有人全权限）
-- ============================================================

INSERT INTO user_role (id, user_id, role_id, created_at)
SELECT nextval('user_role_id_seq'), u.id, 1, NOW()
FROM sys_user u
WHERE NOT EXISTS (
    SELECT 1 FROM user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = 1
);

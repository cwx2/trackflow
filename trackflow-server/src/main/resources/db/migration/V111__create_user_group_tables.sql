-- ============================================================
-- V111: 用户组功能 - 创建用户组相关表
-- ============================================================

-- 用户组定义表
CREATE TABLE user_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    created_by BIGINT REFERENCES sys_user(id),
    updated_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：组名不能重复
CREATE UNIQUE INDEX uk_user_group_name ON user_group(name);

-- 用户组成员关联表
CREATE TABLE user_group_member (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：同一用户不能重复加入同一组
CREATE UNIQUE INDEX uk_group_member ON user_group_member(group_id, user_id);

-- 索引：快速查询用户所属的组
CREATE INDEX idx_group_member_user ON user_group_member(user_id);

-- 用户组角色分配表（支持全局角色 + 项目级角色）
CREATE TABLE user_group_role (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES user_group(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一约束：同组同角色同项目范围不重复
-- project_id = NULL 表示全局角色
CREATE UNIQUE INDEX uk_group_role ON user_group_role(group_id, role_id, COALESCE(project_id, 0));

-- 索引：快速查询组的角色
CREATE INDEX idx_group_role_group ON user_group_role(group_id);

-- 索引：快速查询项目中通过组分配的角色
CREATE INDEX idx_group_role_project ON user_group_role(project_id) WHERE project_id IS NOT NULL;

-- 注册权限：管理用户组
INSERT INTO sys_permission (code, name, description, category, scope, sort_order)
VALUES ('system:manage_groups', '管理用户组', '创建、编辑、删除用户组，管理组成员和组角色', 'system', 'global', 25)
ON CONFLICT (code) DO NOTHING;

-- 为 system_admin 角色赋予管理用户组权限
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'system:manage_groups'
FROM sys_role r WHERE r.code = 'system_admin'
ON CONFLICT (role_id, permission) DO NOTHING;

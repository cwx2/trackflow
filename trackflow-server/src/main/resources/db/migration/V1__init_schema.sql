-- ============================================================
-- TrackFlow 初始化数据库结构
-- ============================================================

-- 组织
CREATE TABLE organization (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_by BIGINT
);

-- 用户（从 Keycloak 同步）
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(100) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    avatar_url VARCHAR(500),
    phone VARCHAR(50),
    org_id BIGINT REFERENCES organization(id),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_by BIGINT
);

CREATE INDEX idx_user_keycloak ON sys_user(keycloak_id);
CREATE INDEX idx_user_org ON sys_user(org_id);
CREATE INDEX idx_user_status ON sys_user(status);

-- 角色
CREATE TABLE sys_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    role_type VARCHAR(20) NOT NULL DEFAULT 'project',
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_by BIGINT
);

-- 角色权限
CREATE TABLE role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    UNIQUE(role_id, permission)
);

CREATE INDEX idx_role_perm_role ON role_permission(role_id);

-- 用户-角色关联（全局角色）
CREATE TABLE user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, role_id)
);

CREATE INDEX idx_user_role_user ON user_role(user_id);
CREATE INDEX idx_user_role_role ON user_role(role_id);

-- API Key
CREATE TABLE api_key (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    key_hash VARCHAR(200) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    permissions JSONB DEFAULT '[]',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_key_user ON api_key(user_id);
CREATE INDEX idx_api_key_prefix ON api_key(prefix);

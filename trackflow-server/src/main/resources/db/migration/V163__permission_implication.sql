-- V163__permission_implication.sql
-- 权限隐含关系表：定义权限之间的依赖/蕴含关系
-- 例如 issue:create 隐含 issue:view，即拥有创建权限的角色自动拥有查看权限
-- 用于角色权限分配时自动补全隐含权限、移除时级联移除依赖权限

CREATE TABLE sys_permission_implication (
    id          BIGSERIAL PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,   -- 上层权限（拥有此权限时...）
    implied_code    VARCHAR(100) NOT NULL,   -- 隐含的底层权限（...自动拥有此权限）
    CONSTRAINT uk_permission_implication UNIQUE (permission_code, implied_code)
);

COMMENT ON TABLE sys_permission_implication IS '权限隐含关系表：permission_code 隐含 implied_code';
COMMENT ON COLUMN sys_permission_implication.permission_code IS '上层权限编码';
COMMENT ON COLUMN sys_permission_implication.implied_code IS '被隐含的底层权限编码';

-- 种子数据：Issue 相关权限都隐含 issue:view
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('issue:create', 'issue:view'),
    ('issue:edit', 'issue:view'),
    ('issue:delete', 'issue:view'),
    ('issue:assign', 'issue:view'),
    ('issue:move', 'issue:view'),
    ('issue:manage_links', 'issue:view'),
    ('issue:manage_tags', 'issue:view')
ON CONFLICT DO NOTHING;

-- 评论权限链：manage > edit/delete > create > issue:view
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('comment:create', 'issue:view'),
    ('comment:edit', 'comment:create'),
    ('comment:delete', 'comment:create'),
    ('comment:manage', 'comment:create'),
    ('comment:manage', 'comment:edit'),
    ('comment:manage', 'comment:delete')
ON CONFLICT DO NOTHING;

-- 附件权限链
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('attachment:upload', 'issue:view'),
    ('attachment:delete', 'attachment:upload')
ON CONFLICT DO NOTHING;

-- Sprint 权限
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('sprint:manage', 'sprint:view')
ON CONFLICT DO NOTHING;

-- 工时权限链
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('time:log', 'issue:view'),
    ('time:edit_all', 'time:log'),
    ('time:view_others', 'time:log')
ON CONFLICT DO NOTHING;

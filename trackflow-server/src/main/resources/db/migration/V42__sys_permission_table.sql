-- ============================================================
-- V42: 权限定义表 - 将硬编码权限迁移到数据库
-- ============================================================

CREATE TABLE sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) UNIQUE NOT NULL,        -- 权限代码，如 'issue:create'
    name        VARCHAR(200) NOT NULL,               -- 显示名称，如 '创建工单'
    category    VARCHAR(50) NOT NULL,                -- 分类，如 'issue'
    scope       VARCHAR(20) NOT NULL DEFAULT 'project',  -- global / project
    description TEXT,                                -- 详细描述
    sort_order  INT NOT NULL DEFAULT 0,              -- 分类内排序
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,       -- 是否启用
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 创建索引
CREATE INDEX idx_sys_permission_category ON sys_permission(category);
CREATE INDEX idx_sys_permission_scope ON sys_permission(scope);

-- ============================================================
-- 种子数据：迁移 ALL_PERMISSIONS 硬编码内容
-- ============================================================

-- system 分类（全局权限）
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('system:admin',           '系统管理员',       'system', 'global', '拥有系统所有权限的超级管理员标识', 1),
('system:manage_users',    '管理用户',         'system', 'global', '创建、编辑、禁用系统用户', 2),
('system:manage_roles',    '管理角色',         'system', 'global', '创建、编辑角色及分配权限', 3),
('system:manage_orgs',     '管理组织',         'system', 'global', '创建、编辑组织信息', 4);

-- project 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('project:create',               '创建项目',       'project', 'global', '创建新项目', 1),
('project:edit',                 '编辑项目',       'project', 'project', '修改项目基本信息', 2),
('project:delete',               '删除项目',       'project', 'project', '删除项目及所有关联数据', 3),
('project:view',                 '查看项目',       'project', 'project', '查看项目详情和内容', 4),
('project:manage_members',       '管理项目成员',   'project', 'project', '添加、移除项目成员及变更角色', 5),
('project:manage_workflow',      '管理工作流',     'project', 'project', '配置项目的工作流状态和转换规则', 6),
('project:manage_custom_fields', '管理自定义字段', 'project', 'project', '定义和管理项目的自定义字段', 7);

-- issue 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('issue:create',              '创建工单',       'issue', 'project', '在项目中创建新工单', 1),
('issue:view',                '查看工单',       'issue', 'project', '查看工单详情和列表', 2),
('issue:edit',                '编辑工单',       'issue', 'project', '修改工单的标题、描述和字段', 3),
('issue:delete',              '删除工单',       'issue', 'project', '永久删除工单', 4),
('issue:assign',              '分配工单',       'issue', 'project', '将工单分配给项目成员', 5),
('issue:change_status',       '变更状态',       'issue', 'project', '变更工单的工作流状态', 6),
('issue:comment',             '评论',           'issue', 'project', '在工单下发表评论', 7),
('issue:manage_attachments',  '管理附件',       'issue', 'project', '上传和删除工单附件', 8);

-- sprint 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('sprint:create', '创建迭代',   'sprint', 'project', '创建新的 Sprint 迭代', 1),
('sprint:edit',   '编辑迭代',   'sprint', 'project', '修改迭代名称、日期等信息', 2),
('sprint:delete', '删除迭代',   'sprint', 'project', '删除迭代（工单回到 Backlog）', 3),
('sprint:view',   '查看迭代',   'sprint', 'project', '查看迭代列表和详情', 4);

-- query 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('query:create',        '创建查询',     'query', 'project', '创建个人保存查询', 1),
('query:share',         '分享查询',     'query', 'project', '将查询分享给项目成员', 2),
('query:manage_public', '管理公共查询', 'query', 'project', '编辑和删除公共查询', 3);

-- report 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('report:view',   '查看报表', 'report', 'project', '查看项目报表和统计', 1),
('report:create', '创建报表', 'report', 'project', '创建自定义报表', 2);

-- integration 分类
INSERT INTO sys_permission (code, name, category, scope, description, sort_order) VALUES
('webhook:manage', '管理 Webhook', 'integration', 'project', '配置项目的 Webhook 集成', 1);

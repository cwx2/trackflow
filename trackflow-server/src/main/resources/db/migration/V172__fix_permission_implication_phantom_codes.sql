-- V172__fix_permission_implication_phantom_codes.sql
-- 修正 sys_permission_implication 表中引用不存在权限码的问题（REQ-153）
--
-- 问题：原始数据使用了 comment:create、comment:edit、comment:delete、comment:manage、
--       attachment:upload、attachment:delete、issue:manage_links、issue:manage_tags、sprint:manage
--       等在 sys_permission 表中不存在的权限码（phantom codes）。
-- 影响：隐含权限机制在运行时形同虚设，RoleService.replacePermissions() 补全的权限码
--       无法被 PermissionService.hasPermission() 匹配到。
-- 修复：将所有 phantom codes 替换为 sys_permission 表中实际存在的权限码。

-- Step 1: 清空旧的隐含关系数据
DELETE FROM sys_permission_implication;

-- Step 2: 插入修正后的隐含关系（所有权限码均存在于 sys_permission 表中）
-- 规则说明：
--   permission_code 是"上层权限"，implied_code 是"被隐含的底层权限"
--   拥有上层权限时，自动隐含拥有底层权限

-- Issue 模块基础隐含关系：所有 Issue 操作都隐含 issue:view
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('issue:create', 'issue:view'),
    ('issue:edit', 'issue:view'),
    ('issue:delete', 'issue:view'),
    ('issue:assign', 'issue:view'),
    ('issue:move', 'issue:view'),
    ('issue:change_status', 'issue:view'),
    ('issue:comment', 'issue:view'),
    ('issue:manage_attachments', 'issue:view');

-- 评论管理隐含关系：管理评论 → 评论
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('issue:manage_comments', 'issue:comment');

-- Sprint 模块隐含关系：所有 Sprint 操作都隐含 sprint:view
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('sprint:create', 'sprint:view'),
    ('sprint:edit', 'sprint:view'),
    ('sprint:delete', 'sprint:view');

-- 工时模块隐含关系
INSERT INTO sys_permission_implication (permission_code, implied_code) VALUES
    ('time:log', 'issue:view'),
    ('time:edit_all', 'time:log'),
    ('time:view_others', 'time:log');

-- Step 3: 清理 role_permission 表中可能存在的 phantom codes
-- 这些 phantom codes 是之前 RoleService.replacePermissions() 通过错误的 implication 数据写入的
DELETE FROM role_permission WHERE permission NOT IN (SELECT code FROM sys_permission);

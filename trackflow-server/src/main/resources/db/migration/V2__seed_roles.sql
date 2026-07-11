-- ============================================================
-- 内置角色种子数据
-- ============================================================

INSERT INTO sys_role (id, name, code, description, role_type, builtin, sort_order) VALUES
(1, '系统管理员', 'system_admin', '拥有系统全部权限', 'global', true, 1),
(2, '项目管理员', 'project_admin', '项目内全部权限，管理成员/工作流/字段', 'project', true, 2),
(3, '开发人员', 'developer', 'Issue CRUD + 状态变更 + Sprint 查看', 'project', true, 3),
(4, '测试人员', 'tester', 'Issue 查看/评论/状态变更（Testing→Done）', 'project', true, 4),
(5, '观察者', 'observer', 'Issue 只读 + 评论', 'project', true, 5);

-- 重置序列以避免冲突
SELECT setval('sys_role_id_seq', 100);

-- V221: 清理 sys_role 表中的测试脏数据
-- 
-- 问题描述：
-- sys_role 表中存在两条测试脏数据 "Test Role Source" 和 "Test Role Target"，
-- 它们的 sort_order=0，导致在工作流编辑器的角色选择器中排在最前面，
-- 覆盖了真实系统角色的显示。
--
-- 修复方案：删除这两条无关联的测试数据

DELETE FROM sys_role 
WHERE code IN ('test_merge_source', 'test_merge_target');

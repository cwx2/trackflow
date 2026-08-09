-- V286__fix_observer_global_role_assignments.sql
-- 修正测试用户的全局角色分配，使其与文档 test-accounts.md 中的角色矩阵一致。
--
-- 问题：huanglei（观察者）和 wangqiang（开发人员）被错误分配了「用户管理员」(id=10)
-- 和「项目创建者」(id=11) 全局角色，导致他们可以访问管理后台。
-- sunlei（产品经理）被错误分配了「系统管理员」(id=1) 全局角色。
--
-- 正确的全局角色矩阵（仅以下用户应有全局角色）：
--   testuser  → 系统管理员 (id=1)
--   lina      → 系统管理员 (id=1)
-- 其他所有用户不应有全局角色（项目级角色通过 project_member 表管理）。
--
-- Closes REQ-456

-- 1. 移除 huanglei (id=1008) 的所有全局角色（用户管理员 + 项目创建者）
DELETE FROM user_role WHERE user_id = 1008;

-- 2. 移除 wangqiang (id=1003) 的所有全局角色（用户管理员 + 项目创建者）
DELETE FROM user_role WHERE user_id = 1003;

-- 3. 移除 sunlei (id=1009) 的系统管理员角色（产品经理不应有全局管理权限）
DELETE FROM user_role WHERE user_id = 1009;

-- 4. 移除 niurui (id=209) 的用户管理员角色（非核心测试用户，不应有管理权限）
DELETE FROM user_role WHERE user_id = 209 AND role_id = 10;

-- 验证：修正后 user_role 表应只剩：
--   testuser (id=2074817502681837570) → 系统管理员 (role_id=1)
--   lina (id=1002) → 系统管理员 (role_id=1)

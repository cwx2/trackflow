-- ============================================================
-- 修正测试用户角色分配，使其与项目文档设计一致
-- REQ-001: zhangwei 不应有 system_admin 全局角色
-- ============================================================

-- 1. 移除 zhangwei (id=1001) 的 system_admin 全局角色
-- zhangwei 是开发人员，不应有系统管理员权限
DELETE FROM user_role
WHERE user_id = 1001 AND role_id = 1;

-- 2. 修正 zhangwei 在项目中的角色：project_admin → developer
-- zhangwei 应为开发人员 (role_id=3)，而非项目管理员 (role_id=2)
UPDATE project_member
SET role_id = 3
WHERE user_id = 1001 AND role_id = 2;

-- 3. 修正 zhaojing (id=1004) 在项目中的角色：developer → tester
-- zhaojing 是测试人员 (role_id=4)，而非开发人员 (role_id=3)
UPDATE project_member
SET role_id = 4
WHERE user_id = 1004 AND role_id = 3;

-- 角色矩阵（修正后）:
-- testuser  → system_admin (全局) + project_admin (项目级)
-- lina      → system_admin (全局) + project_admin (项目级) [团队负责人]
-- zhangwei  → 无全局角色 + developer (项目级) [开发人员]
-- wangqiang → 无全局角色 + developer (项目级) [普通成员]
-- zhaojing  → 无全局角色 + tester (项目级) [测试人员]

-- V173__remove_global_permissions_from_project_admin_role.sql
-- 从 project_admin 角色中移除不应存在的全局权限（project:create, rule:view_statistics）
-- 这些权限的 scope 是 'global'，不应分配给项目级角色（role_type='project'）
-- SQL 层 scope 过滤（同版本代码修复）已确保这些权限不会在项目上下文中生效
-- 此迁移额外清理数据，确保数据本身的正确性

DELETE FROM role_permission
WHERE role_id = (SELECT id FROM sys_role WHERE code = 'project_admin')
  AND permission IN (
    SELECT code FROM sys_permission WHERE scope = 'global'
  );

-- ============================================================
-- V97: 修复安全漏洞——移除 developer 角色的 issue:delete 权限
--
-- 根因：developer (role_id=3) 被错误授予了 issue:delete 权限，
-- 导致任何开发人员都可以删除工单（包括他人创建的）。
-- 按照 YouTrack 标准，仅 project_admin 和 tech_lead 应有删除权限。
--
-- Closes REQ-361
-- ============================================================

DELETE FROM role_permission
WHERE role_id = 3 AND permission = 'issue:delete';

-- V240: 移除观察者角色的评论权限
-- 根据 YouTrack 权限模型，Observer 角色不应拥有 Create Issue Comment 权限
-- 参考：youtrack-docs/pages/2025.3__default-roles.html.md
-- REQ-78

-- 删除观察者角色的 issue:comment 权限
DELETE FROM role_permission
WHERE role_id = (SELECT id FROM sys_role WHERE name = '观察者')
  AND permission = 'issue:comment';

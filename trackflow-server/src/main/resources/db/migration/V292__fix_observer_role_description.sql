-- 修复观察者角色描述与实际权限不一致的问题
-- REQ-78 移除了观察者的 issue:comment 权限，但未同步更新 description 字段
-- 将描述从"Issue 只读 + 评论"更正为"Issue 只读"

UPDATE sys_role SET description = 'Issue 只读' WHERE id = 5 AND description = 'Issue 只读 + 评论';

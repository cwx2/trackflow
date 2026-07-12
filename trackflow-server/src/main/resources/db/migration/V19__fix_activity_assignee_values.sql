-- ============================================================
-- 修复 issue_activity 中 assignee 字段存储的显示名为用户 ID
-- 确保 SQL 层的 CASE + LEFT JOIN 逻辑能正确解析历史数据
-- ============================================================

-- 将 assignee 活动记录中存储的非数字显示名替换为对应的用户 ID
UPDATE issue_activity a
SET old_value = u.id::TEXT
FROM sys_user u
WHERE a.field_name = 'assignee'
  AND a.old_value IS NOT NULL
  AND a.old_value NOT SIMILAR TO '[0-9]+'
  AND (u.display_name = a.old_value OR u.username = a.old_value);

UPDATE issue_activity a
SET new_value = u.id::TEXT
FROM sys_user u
WHERE a.field_name = 'assignee'
  AND a.new_value IS NOT NULL
  AND a.new_value NOT SIMILAR TO '[0-9]+'
  AND (u.display_name = a.new_value OR u.username = a.new_value);

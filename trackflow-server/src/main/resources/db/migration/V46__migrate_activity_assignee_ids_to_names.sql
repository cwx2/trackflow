-- ============================================================
-- 将 issue_activity 中 assignee 字段的 userId 值迁移为用户显示名
-- 此后 Service 层记录 assignee 变更时直接存储显示名快照，无需 SQL 层实时解析
-- ============================================================

-- 将 old_value 中的数字 userId 替换为对应的 display_name
UPDATE issue_activity a
SET old_value = u.display_name
FROM sys_user u
WHERE a.field_name IN ('assignee', 'assignee_id')
  AND a.old_value IS NOT NULL
  AND a.old_value SIMILAR TO '[0-9]+'
  AND u.id = CAST(a.old_value AS BIGINT);

-- 将 new_value 中的数字 userId 替换为对应的 display_name
UPDATE issue_activity a
SET new_value = u.display_name
FROM sys_user u
WHERE a.field_name IN ('assignee', 'assignee_id')
  AND a.new_value IS NOT NULL
  AND a.new_value SIMILAR TO '[0-9]+'
  AND u.id = CAST(a.new_value AS BIGINT);

-- 修复历史数据中字符串 "null"（旧代码 String.valueOf(null) 的产物），置为真正的 NULL
UPDATE issue_activity
SET old_value = NULL
WHERE field_name IN ('assignee', 'assignee_id')
  AND old_value = 'null';

UPDATE issue_activity
SET new_value = NULL
WHERE field_name IN ('assignee', 'assignee_id')
  AND new_value = 'null';

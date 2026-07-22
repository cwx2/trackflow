-- V170__fix_assignee_activity_display_values.sql
-- 修复历史活动记录中负责人变更显示用户 ID 而非显示名的问题。
-- 将 old_value/new_value 为纯数字（用户 ID）且 display_value 为空的记录，
-- 通过 JOIN sys_user 表补填 old_display_value/new_display_value。

-- 修复 new_value 为用户 ID 的记录
UPDATE issue_activity a
SET new_display_value = u.display_name
FROM sys_user u
WHERE a.field_name IN ('assignee_id', 'assignee')
  AND a.new_display_value IS NULL
  AND a.new_value IS NOT NULL
  AND a.new_value ~ '^\d+$'
  AND u.id = CAST(a.new_value AS BIGINT);

-- 修复 old_value 为用户 ID 的记录
UPDATE issue_activity a
SET old_display_value = u.display_name
FROM sys_user u
WHERE a.field_name IN ('assignee_id', 'assignee')
  AND a.old_display_value IS NULL
  AND a.old_value IS NOT NULL
  AND a.old_value ~ '^\d+$'
  AND u.id = CAST(a.old_value AS BIGINT);

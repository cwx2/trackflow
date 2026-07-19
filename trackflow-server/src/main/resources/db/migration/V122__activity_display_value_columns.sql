-- V122: issue_activity 增加展示值列 + Sprint 活动记录回填 ID
-- REQ-471: 燃尽图算法通过 Sprint 名称匹配导致重命名后数据丢失

-- 1. 新增 old_display_value 和 new_display_value 列
-- 用途：当 old_value/new_value 存储 ID 时，display 列存储人类可读名称
ALTER TABLE issue_activity ADD COLUMN IF NOT EXISTS old_display_value VARCHAR(500);
ALTER TABLE issue_activity ADD COLUMN IF NOT EXISTS new_display_value VARCHAR(500);

COMMENT ON COLUMN issue_activity.old_display_value IS '旧值的展示文本（当 old_value 存储 ID 时，此列存储对应名称）';
COMMENT ON COLUMN issue_activity.new_display_value IS '新值的展示文本（当 new_value 存储 ID 时，此列存储对应名称）';

-- 2. 回填历史 sprint 活动记录：将 name 移到 display 列，value 列改为 Sprint ID
-- 策略：通过 sprint.name 精确匹配反查 sprint.id
-- 注意：同名 Sprint 可能存在于不同项目，但活动记录通过 issue_id → issue.sprint_id 可区分
-- 为安全起见，优先使用 issue.sprint_id 关联（当前工单在哪个 Sprint）

-- 2a. 回填 new_value（移入记录）：通过 sprint 名称匹配 ID
-- 先把当前 name 复制到 display 列
UPDATE issue_activity
SET new_display_value = new_value
WHERE field_name = 'sprint'
  AND new_value IS NOT NULL
  AND new_display_value IS NULL
  AND new_value !~ '^\d+$';  -- 只处理非纯数字的（即还是名称的记录）

-- 然后将 new_value 替换为 Sprint ID（通过名称反查）
UPDATE issue_activity a
SET new_value = s.id::text
FROM sprint s
WHERE a.field_name = 'sprint'
  AND a.new_display_value IS NOT NULL
  AND a.new_display_value = s.name
  AND a.new_value = a.new_display_value;  -- 确保还没被转换过

-- 2b. 回填 old_value（移出记录）：通过 sprint 名称匹配 ID
UPDATE issue_activity
SET old_display_value = old_value
WHERE field_name = 'sprint'
  AND old_value IS NOT NULL
  AND old_display_value IS NULL
  AND old_value !~ '^\d+$';

UPDATE issue_activity a
SET old_value = s.id::text
FROM sprint s
WHERE a.field_name = 'sprint'
  AND a.old_display_value IS NOT NULL
  AND a.old_display_value = s.name
  AND a.old_value = a.old_display_value;

-- 2c. 处理已删除 Sprint 的情况：如果名称无法匹配到 sprint 表中的记录
-- （Sprint 已被删除），保留原始名称在 display 列，value 列保持不变
-- 这些记录无法通过 ID 匹配到燃尽图查询（Sprint 已不存在），不影响功能

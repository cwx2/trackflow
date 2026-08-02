-- V236__fix_issue_status_inconsistency.sql
-- 修复工单状态字段与活动历史不一致的数据问题
-- REQ-22: 工单状态字段显示与实际状态不一致
--
-- 问题背景：
-- 发现部分工单的 issue.status_id 与 issue_activity 表中最后一条 status_changed 记录不一致。
-- 例如 DE4-1391：status_id=4 (Testing)，但最后一条活动记录显示变更为「进行中」。
--
-- 修复策略：
-- 根据活动历史中最后一条 status_changed 记录的 new_value（状态显示名）反查 issue_status 表，
-- 将 issue.status_id 更新为正确的状态 ID。

-- 首先创建临时表，找出所有需要修复的工单
CREATE TEMP TABLE issues_to_fix AS
WITH last_status_activity AS (
    SELECT DISTINCT ON (ia.issue_id)
        ia.issue_id,
        ia.new_value AS last_status_name,
        ia.created_at
    FROM issue_activity ia
    WHERE ia.action = 'status_changed'
    ORDER BY ia.issue_id, ia.created_at DESC
)
SELECT
    i.id AS issue_id,
    i.status_id AS current_status_id,
    cs.name AS current_status_name,
    cs.display_name AS current_status_display,
    lsa.last_status_name,
    COALESCE(
        (SELECT s.id FROM issue_status s WHERE s.name = lsa.last_status_name),
        (SELECT s.id FROM issue_status s WHERE s.display_name = lsa.last_status_name)
    ) AS correct_status_id
FROM issue i
JOIN last_status_activity lsa ON lsa.issue_id = i.id
JOIN issue_status cs ON cs.id = i.status_id
WHERE i.deleted_at IS NULL
  -- 当前状态名和显示名都不匹配活动记录的最后状态
  AND cs.name != lsa.last_status_name
  AND (cs.display_name IS NULL OR cs.display_name != lsa.last_status_name);

-- 执行更新：仅更新能找到正确状态 ID 的记录
UPDATE issue i
SET 
    status_id = itf.correct_status_id,
    updated_at = NOW()
FROM issues_to_fix itf
WHERE i.id = itf.issue_id
  AND itf.correct_status_id IS NOT NULL
  AND i.status_id != itf.correct_status_id;

-- 记录无法自动修复的情况（找不到匹配的状态 ID）
DO $$
DECLARE
    unfixable_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO unfixable_count
    FROM issues_to_fix
    WHERE correct_status_id IS NULL;
    
    IF unfixable_count > 0 THEN
        RAISE NOTICE 'V236: % issue(s) could not be auto-fixed (status name not found in issue_status table)', unfixable_count;
    END IF;
END $$;

-- 清理临时表
DROP TABLE IF EXISTS issues_to_fix;

-- 输出修复统计
DO $$
DECLARE
    fixed_count INTEGER;
BEGIN
    -- 重新统计修复后的不一致数量
    SELECT COUNT(*) INTO fixed_count
    FROM issue i
    JOIN issue_status cs ON cs.id = i.status_id
    WHERE i.deleted_at IS NULL
      AND EXISTS (
          SELECT 1 FROM (
              SELECT DISTINCT ON (ia.issue_id)
                  ia.issue_id,
                  ia.new_value
              FROM issue_activity ia
              WHERE ia.action = 'status_changed'
              ORDER BY ia.issue_id, ia.created_at DESC
          ) lsa
          WHERE lsa.issue_id = i.id
            AND cs.name != lsa.new_value
            AND (cs.display_name IS NULL OR cs.display_name != lsa.new_value)
      );
    
    IF fixed_count = 0 THEN
        RAISE NOTICE 'V236: All status inconsistencies have been fixed';
    ELSE
        RAISE NOTICE 'V236: % issue(s) still have status inconsistency after fix', fixed_count;
    END IF;
END $$;

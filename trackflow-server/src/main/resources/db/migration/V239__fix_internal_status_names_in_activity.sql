-- V239: 修复活动历史中的内部/测试状态名称
-- 
-- 问题：历史活动记录中存在 "TestOnlyStatus" 等内部测试状态名，这些名称不应暴露给用户
-- 解决：将这些内部名称更新为友好的展示值 "(历史状态)"

-- 1. 修复 old_value 中的 TestOnlyStatus
UPDATE issue_activity
SET old_value = '(历史状态)'
WHERE field_name = 'status' 
  AND old_value = 'TestOnlyStatus';

-- 2. 修复 new_value 中的 TestOnlyStatus（如果有的话）
UPDATE issue_activity
SET new_value = '(历史状态)'
WHERE field_name = 'status' 
  AND new_value = 'TestOnlyStatus';

-- 3. 修复其他可能的内部状态名（全大写下划线格式，如 IN_PROGRESS_TEST）
-- 这些是明显的内部枚举值，不应展示给用户
UPDATE issue_activity
SET old_value = '(历史状态)'
WHERE field_name = 'status' 
  AND old_value ~ '^[A-Z][A-Z_0-9]+$'
  AND old_value !~ '^(OPEN|TODO|DONE)$';  -- 排除可能合法的简短状态

UPDATE issue_activity
SET new_value = '(历史状态)'
WHERE field_name = 'status' 
  AND new_value ~ '^[A-Z][A-Z_0-9]+$'
  AND new_value !~ '^(OPEN|TODO|DONE)$';

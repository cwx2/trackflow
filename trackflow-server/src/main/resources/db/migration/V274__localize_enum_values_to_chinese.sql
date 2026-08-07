-- V274__localize_enum_values_to_chinese.sql
-- 将系统内置枚举数据从英文值迁移为中文值
-- 业务数据标签（Priority名、IssueType名）由管理员配置决定显示语言，
-- 按 OpenProject 标准做法：name/value 字段直接存显示文本，不走翻译中间层。
-- 迁移范围：custom_field_option.value, issue.priority, issue.issue_type,
--           issue_activity.old_value/new_value, saved_query.filters(JSONB)

-- ========================================
-- 1. 优先级选项值：英文 → 中文
-- ========================================
UPDATE custom_field_option SET value = '阻塞'
WHERE custom_field_id = 1000000000000000001 AND value = 'Show-stopper';

UPDATE custom_field_option SET value = '紧急'
WHERE custom_field_id = 1000000000000000001 AND value = 'Critical';

UPDATE custom_field_option SET value = '高'
WHERE custom_field_id = 1000000000000000001 AND value = 'High';

UPDATE custom_field_option SET value = '普通'
WHERE custom_field_id = 1000000000000000001 AND value = 'Normal';

UPDATE custom_field_option SET value = '低'
WHERE custom_field_id = 1000000000000000001 AND value = 'Low';

-- ========================================
-- 2. 工单类型选项值：英文 → 中文
-- ========================================
UPDATE custom_field_option SET value = '缺陷'
WHERE custom_field_id = 1000000000000000002 AND value = 'Bug';

UPDATE custom_field_option SET value = '任务'
WHERE custom_field_id = 1000000000000000002 AND value = 'Task';

UPDATE custom_field_option SET value = '需求'
WHERE custom_field_id = 1000000000000000002 AND value = 'Feature';

UPDATE custom_field_option SET value = '史诗'
WHERE custom_field_id = 1000000000000000002 AND value = 'Epic';

UPDATE custom_field_option SET value = '故事'
WHERE custom_field_id = 1000000000000000002 AND value = 'Story';

-- ========================================
-- 3. issue.priority 列：英文 → 中文
-- ========================================
UPDATE issue SET priority = '阻塞' WHERE priority = 'Show-stopper';
UPDATE issue SET priority = '紧急' WHERE priority = 'Critical';
UPDATE issue SET priority = '高' WHERE priority = 'High';
UPDATE issue SET priority = '普通' WHERE priority = 'Normal';
UPDATE issue SET priority = '普通' WHERE priority = 'medium';
UPDATE issue SET priority = '低' WHERE priority = 'Low';

-- ========================================
-- 4. issue.issue_type 列：英文 → 中文
-- ========================================
UPDATE issue SET issue_type = '缺陷' WHERE issue_type = 'Bug';
UPDATE issue SET issue_type = '任务' WHERE issue_type = 'Task';
UPDATE issue SET issue_type = '需求' WHERE issue_type = 'Feature';
UPDATE issue SET issue_type = '史诗' WHERE issue_type = 'Epic';
UPDATE issue SET issue_type = '故事' WHERE issue_type = 'Story';

-- ========================================
-- 5. issue_activity：历史变更记录中的优先级和类型值
-- ========================================
-- Priority old_value
UPDATE issue_activity SET old_value = '阻塞' WHERE field_name = 'priority' AND old_value = 'Show-stopper';
UPDATE issue_activity SET old_value = '紧急' WHERE field_name = 'priority' AND old_value = 'Critical';
UPDATE issue_activity SET old_value = '高' WHERE field_name = 'priority' AND old_value = 'High';
UPDATE issue_activity SET old_value = '普通' WHERE field_name = 'priority' AND (old_value = 'Normal' OR old_value = 'medium');
UPDATE issue_activity SET old_value = '低' WHERE field_name = 'priority' AND old_value = 'Low';

-- Priority new_value
UPDATE issue_activity SET new_value = '阻塞' WHERE field_name = 'priority' AND new_value = 'Show-stopper';
UPDATE issue_activity SET new_value = '紧急' WHERE field_name = 'priority' AND new_value = 'Critical';
UPDATE issue_activity SET new_value = '高' WHERE field_name = 'priority' AND new_value = 'High';
UPDATE issue_activity SET new_value = '普通' WHERE field_name = 'priority' AND (new_value = 'Normal' OR new_value = 'medium');
UPDATE issue_activity SET new_value = '低' WHERE field_name = 'priority' AND new_value = 'Low';

-- IssueType old_value
UPDATE issue_activity SET old_value = '缺陷' WHERE field_name = 'issue_type' AND old_value = 'Bug';
UPDATE issue_activity SET old_value = '任务' WHERE field_name = 'issue_type' AND old_value = 'Task';
UPDATE issue_activity SET old_value = '需求' WHERE field_name = 'issue_type' AND old_value = 'Feature';
UPDATE issue_activity SET old_value = '史诗' WHERE field_name = 'issue_type' AND old_value = 'Epic';
UPDATE issue_activity SET old_value = '故事' WHERE field_name = 'issue_type' AND old_value = 'Story';

-- IssueType new_value
UPDATE issue_activity SET new_value = '缺陷' WHERE field_name = 'issue_type' AND new_value = 'Bug';
UPDATE issue_activity SET new_value = '任务' WHERE field_name = 'issue_type' AND new_value = 'Task';
UPDATE issue_activity SET new_value = '需求' WHERE field_name = 'issue_type' AND new_value = 'Feature';
UPDATE issue_activity SET new_value = '史诗' WHERE field_name = 'issue_type' AND new_value = 'Epic';
UPDATE issue_activity SET new_value = '故事' WHERE field_name = 'issue_type' AND new_value = 'Story';

-- ========================================
-- 6. saved_query.filters (JSONB): 替换优先级和类型的英文值
-- ========================================
-- Priority values in saved queries
UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Show-stopper"', '"阻塞"', 'g')::jsonb
WHERE filters::text LIKE '%Show-stopper%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Critical"', '"紧急"', 'g')::jsonb
WHERE filters::text LIKE '%Critical%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"High"', '"高"', 'g')::jsonb
WHERE filters::text LIKE '%High%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Normal"', '"普通"', 'g')::jsonb
WHERE filters::text LIKE '%Normal%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Low"', '"低"', 'g')::jsonb
WHERE filters::text LIKE '%Low%';

-- IssueType values in saved queries
UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Bug"', '"缺陷"', 'g')::jsonb
WHERE filters::text LIKE '%"Bug"%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Task"', '"任务"', 'g')::jsonb
WHERE filters::text LIKE '%"Task"%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Feature"', '"需求"', 'g')::jsonb
WHERE filters::text LIKE '%"Feature"%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Epic"', '"史诗"', 'g')::jsonb
WHERE filters::text LIKE '%"Epic"%';

UPDATE saved_query
SET filters = regexp_replace(filters::text, '"Story"', '"故事"', 'g')::jsonb
WHERE filters::text LIKE '%"Story"%';

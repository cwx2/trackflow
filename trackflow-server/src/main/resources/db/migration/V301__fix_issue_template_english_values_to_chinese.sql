-- V301__fix_issue_template_english_values_to_chinese.sql
-- 修复 issue_template 表中 issue_type 和 priority 字段仍为英文值的问题
-- V274 将 custom_field_option/issue/issue_activity/saved_query 中的枚举值全面中文化，
-- 但遗漏了 issue_template 表，导致模板创建工单时传英文值被后端拒绝（400 Bad Request）

-- issue_type: 英文 → 中文
UPDATE issue_template SET issue_type = '缺陷' WHERE issue_type = 'Bug';
UPDATE issue_template SET issue_type = '任务' WHERE issue_type = 'Task';
UPDATE issue_template SET issue_type = '需求' WHERE issue_type = 'Feature';
UPDATE issue_template SET issue_type = '史诗' WHERE issue_type = 'Epic';
UPDATE issue_template SET issue_type = '故事' WHERE issue_type = 'Story';

-- priority: 英文 → 中文
UPDATE issue_template SET priority = '阻塞' WHERE priority = 'Show-stopper';
UPDATE issue_template SET priority = '紧急' WHERE priority = 'Critical';
UPDATE issue_template SET priority = '高' WHERE priority = 'High';
UPDATE issue_template SET priority = '普通' WHERE priority = 'Normal';
UPDATE issue_template SET priority = '低' WHERE priority = 'Low';

-- V248: 将系统内置关联类型的展示名改为中文
-- 原英文名（blocks/duplicates/relates to/parent of）对中文用户不直观

UPDATE issue_link_type SET outward_name = '阻塞',   inward_name = '被阻塞' WHERE name = 'blocks';
UPDATE issue_link_type SET outward_name = '重复',   inward_name = '被重复' WHERE name = 'duplicates';
UPDATE issue_link_type SET outward_name = '相关',   inward_name = '相关'   WHERE name = 'relates_to';
UPDATE issue_link_type SET outward_name = '父任务', inward_name = '子任务' WHERE name = 'parent_of';

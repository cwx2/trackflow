-- V247__normalize_issue_type_data.sql
-- 修正 issue_type 字段中的脏数据：将非标准大小写统一为标准值
-- 根因：创建/更新工单时未对 issue_type 做枚举校验，导致 'bug'、'task' 等非标准值写入

UPDATE issue SET issue_type = 'Bug' WHERE issue_type = 'bug';
UPDATE issue SET issue_type = 'Task' WHERE issue_type = 'task';
UPDATE issue SET issue_type = 'Feature' WHERE issue_type = 'feature';
UPDATE issue SET issue_type = 'Epic' WHERE issue_type = 'epic';
UPDATE issue SET issue_type = 'Story' WHERE issue_type = 'story';

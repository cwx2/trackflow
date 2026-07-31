-- V222__fix_board_name_english_to_chinese.sql
-- 修复看板名称：将英文 "Sprint Board" 清空，让前端 fallback 到默认的中文"项目名 看板"
-- 关联需求：REQ-933

UPDATE board_general_config
SET name = ''
WHERE name = 'Sprint Board';

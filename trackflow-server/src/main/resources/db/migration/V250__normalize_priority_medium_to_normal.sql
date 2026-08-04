-- V250: 修正遗留的 'medium' 优先级脏数据为 'Normal'
-- V249 遗漏了 medium → Normal 的规范化，此脚本补充处理

UPDATE issue SET priority = 'Normal' WHERE LOWER(priority) = 'medium';

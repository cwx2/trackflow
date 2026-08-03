-- V175__update_saved_query_current_sprint_variable.sql
-- 将 "FE1 当前迭代" Saved Query 的硬编码 sprint_id 替换为 ${currentSprint} 动态变量，
-- 使其自动匹配项目中当前活跃的 Sprint 而非固定引用已完成的 Sprint 3。

UPDATE saved_query
SET filters = '[{"field":"sprint","value":["${currentSprint}"],"operator":"eq"},{"field":"status","value":[],"operator":"open"}]'
WHERE id = 17 AND name = 'FE1 当前迭代';

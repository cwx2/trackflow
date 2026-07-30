-- V220__fix_saved_query_filters_my_done_issues.sql
-- 修复 Saved Query "我已经做好的工单" 的 filters 字段
-- 问题：filters 为空数组，导致返回所有工单而非当前用户负责的已关闭工单
-- 修复：设置正确的 filters，筛选 assignee = 当前用户 且 status = 已关闭

UPDATE saved_query
SET filters = '[{"field":"assignee","operator":"eq","value":["${currentUser}"]},{"field":"status","operator":"closed","value":[]}]'::jsonb,
    updated_at = NOW()
WHERE id = 2078029867514642433
  AND name = '我已经做好的工单';

-- 同时修复另一个 filters 为空的查询 "最近更新"（id=8）
-- 该查询名称暗示按更新时间排序，但 filters 为空意味着显示所有工单
-- 保持 filters 为空是合理的（显示所有工单按更新时间排序），不做修改
-- 如果需要修改，可以考虑添加时间范围筛选，但目前保持原样

COMMENT ON TABLE saved_query IS 'Saved Queries - filters 格式: [{"field":"xxx","operator":"yyy","value":["zzz"]}]';

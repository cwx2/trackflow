-- REQ-707: 修复"待测试工单"Saved Query 与导航栏 Badge 数量不一致
-- 问题：Badge 统计所有 Testing 状态工单（无 assignee 限制），
--       但 Saved Query 额外过滤了 assignee=${currentUser}，导致数量不匹配
-- 方案：去除 assignee 过滤条件，让 Saved Query 与 Badge 逻辑一致
--       测试人员的工作范围由状态决定，而非负责人字段

UPDATE saved_query
SET filters = '[{"field": "status", "value": ["testing"], "operator": "in"}]'::jsonb,
    updated_at = NOW()
WHERE id = 19
  AND name = '待测试工单';
